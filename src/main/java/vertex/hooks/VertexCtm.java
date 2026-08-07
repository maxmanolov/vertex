package vertex.hooks;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;
import vertex.ctm.BlobConnectivity;
import vertex.ctm.BlobTileOrder;
import vertex.ctm.CtmPackScanner;
import vertex.ctm.CtmProperties;
import vertex.ctm.CtmRuleSet;

/**
 * Connected textures: registers each rule's numbered tile images into the block atlas
 * before it stitches (the only point where new sprites can gain atlas coordinates), then
 * substitutes the connectivity-appropriate tile at the shared icon dispatch.
 *
 * Registration happens once per atlas build; the render path is a map lookup, four
 * neighbour comparisons, and an array index - no allocation.
 */
public final class VertexCtm
{
    public static long registered = 0L;
    public static long applied = 0L;

    private static CtmRuleSet rules;
    private static final Map<CtmProperties, Object[]> tiles = new HashMap<CtmProperties, Object[]>();
    private static final Map<Object, BlockKeys> blockKeyCache = new ConcurrentHashMap<Object, BlockKeys>();
    private static final Map<Object, ConcurrentHashMap<String, CandidateEntry>> candidateCache =
        new ConcurrentHashMap<Object, ConcurrentHashMap<String, CandidateEntry>>();
    private static boolean disabled = false;
    private static Method getBlock;
    private static Method getBlockId;
    private static Object blockRegistry;
    private static Method getBlockRegistryName;

    private static final class BlockKeys
    {
        final String id;
        final String name;

        BlockKeys(String id, String name)
        {
            this.id = id;
            this.name = name;
        }
    }

    private static final class CandidateEntry
    {
        final CtmRuleSet owner;
        final List<CtmProperties> rules;

        CandidateEntry(CtmRuleSet owner, List<CtmProperties> rules)
        {
            this.owner = owner;
            this.rules = rules;
        }
    }

    /** Head hook on TextureMap.loadTextureAtlas, before stitching. */
    public static void beforeStitch(Object textureMap)
    {
        if (disabled || !VertexConfig.enabled("connectedTextures"))
        {
            return;
        }

        try
        {
            tiles.clear();
            candidateCache.clear();
            registered = 0L;
            rules = scanPacks();

            if (rules == null || rules.isEmpty())
            {
                return;
            }

            Method registerIcon = textureMap.getClass().getMethod(Mappings.TEXTUREMAP_REGISTER_ICON, String.class);
            registerIcon.setAccessible(true);

            for (CtmProperties rule : allRules())
            {
                if (rule.directory == null || rule.tiles.length == 0)
                {
                    continue;
                }

                Object[] icons = new Object[rule.tiles.length];

                for (int i = 0; i < rule.tiles.length; ++i)
                {
                    // basePath is textures/blocks, so escaping upward reaches the pack root.
                    String name = "../../" + rule.directory + "/" + rule.tiles[i];
                    icons[i] = registerIcon.invoke(textureMap, name);

                    if (icons[i] != null)
                    {
                        ++registered;
                    }
                }

                tiles.put(rule, icons);
            }

            VertexIcons.activate();
            LogWrapper.info("[Vertex] Connected textures: registered " + registered + " tile sprites for " + rules.size() + " rules");
        }
        catch (Throwable e)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Connected textures disabled after failure");
            e.printStackTrace();
        }
    }

    /** Icon substitution for a face; returns null when no rule applies. */
    public static Object substitute(Object icon, String tileName, Object block, Object world, int x, int y, int z, int side)
    {
        if (disabled || rules == null || rules.isEmpty() || tileName == null)
        {
            return null;
        }

        try
        {
            CtmRuleSet activeRules = rules;
            List<CtmProperties> matching = candidates(activeRules, tileName, block);

            if (matching.isEmpty())
            {
                return null;
            }

            for (CtmProperties rule : matching)
            {
                if (!faceEnabled(rule, side))
                {
                    continue;
                }

                Object[] ruleTiles = tiles.get(rule);

                if (ruleTiles == null || ruleTiles.length == 0)
                {
                    continue;
                }

                if (rule.method != CtmProperties.Method.CTM)
                {
                    // Non-blob methods reuse the first tile until their own dispatch lands.
                    continue;
                }

                int mask = neighbourMask(block, world, x, y, z, side);
                int index = BlobTileOrder.tileIndex(BlobConnectivity.canonical(mask));

                if (index < ruleTiles.length && ruleTiles[index] != null)
                {
                    ++applied;
                    return ruleTiles[index];
                }
            }
        }
        catch (Throwable e)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Connected textures disabled after failure");
            e.printStackTrace();
        }

        return null;
    }

    private static List<CtmProperties> candidates(CtmRuleSet activeRules, String tileName, Object block) throws Exception
    {
        if (block == null)
        {
            return activeRules.matching(tileName, null, null);
        }

        ConcurrentHashMap<String, CandidateEntry> byTile = candidateCache.get(block);

        if (byTile == null)
        {
            ConcurrentHashMap<String, CandidateEntry> created = new ConcurrentHashMap<String, CandidateEntry>();
            ConcurrentHashMap<String, CandidateEntry> raced = candidateCache.putIfAbsent(block, created);
            byTile = raced != null ? raced : created;
        }

        CandidateEntry cached = byTile.get(tileName);

        if (cached != null && cached.owner == activeRules)
        {
            return cached.rules;
        }

        BlockKeys keys = blockKeys(block);
        CandidateEntry created = new CandidateEntry(activeRules,
            activeRules.matching(tileName, keys.id, keys.name));
        byTile.put(tileName, created);
        return created.rules;
    }

    private static BlockKeys blockKeys(Object block) throws Exception
    {
        BlockKeys cached = blockKeyCache.get(block);

        if (cached != null)
        {
            return cached;
        }

        return resolveBlockKeys(block);
    }

    private static synchronized BlockKeys resolveBlockKeys(Object block) throws Exception
    {
        BlockKeys cached = blockKeyCache.get(block);

        if (cached != null)
        {
            return cached;
        }

        if (getBlockId == null)
        {
            Class<?> blockRoot = block.getClass();

            while (blockRoot.getSuperclass() != null && blockRoot.getSuperclass() != Object.class)
            {
                blockRoot = blockRoot.getSuperclass();
            }

            getBlockId = blockRoot.getMethod(Mappings.BLOCK_GET_ID, blockRoot);
            java.lang.reflect.Field registryField = blockRoot.getField(Mappings.BLOCK_REGISTRY);
            blockRegistry = registryField.get(null);
            getBlockRegistryName = blockRegistry.getClass().getMethod(Mappings.REGISTRY_NAME_FOR_OBJECT, Object.class);
        }

        int numericId = ((Integer)getBlockId.invoke(null, block)).intValue();
        String registryName = (String)getBlockRegistryName.invoke(blockRegistry, block);
        BlockKeys resolved = new BlockKeys(String.valueOf(numericId), registryName);
        blockKeyCache.put(block, resolved);
        return resolved;
    }

    private static boolean faceEnabled(CtmProperties rule, int side)
    {
        switch (side)
        {
            case 0:
                return (rule.facesMask & CtmProperties.FACE_BOTTOM) != 0;
            case 1:
                return (rule.facesMask & CtmProperties.FACE_TOP) != 0;
            case 2:
                return (rule.facesMask & CtmProperties.FACE_NORTH) != 0;
            case 3:
                return (rule.facesMask & CtmProperties.FACE_SOUTH) != 0;
            case 4:
                return (rule.facesMask & CtmProperties.FACE_WEST) != 0;
            default:
                return (rule.facesMask & CtmProperties.FACE_EAST) != 0;
        }
    }

    /** In-plane 8-neighbour connectivity for the given face, matching same-block. */
    private static int neighbourMask(Object block, Object world, int x, int y, int z, int side) throws Exception
    {
        if (getBlock == null)
        {
            for (Method method : world.getClass().getMethods())
            {
                if (method.getName().equals(Mappings.IBA_GET_BLOCK) && method.getParameterTypes().length == 3
                    && method.getParameterTypes()[0] == Integer.TYPE)
                {
                    getBlock = method;
                }
            }
        }

        int[][] plane = planeAxes(side);
        int mask = 0;
        int bit = 0;

        for (int[] offset : plane)
        {
            if (same(block, world, x + offset[0], y + offset[1], z + offset[2]))
            {
                mask |= 1 << bit;
            }

            ++bit;
        }

        return mask;
    }

    /** Offsets for N,E,S,W then NE,SE,SW,NW in the plane of the given face. */
    private static int[][] planeAxes(int side)
    {
        if (side <= 1)
        {
            return new int[][] {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0},
                {1, 0, -1}, {1, 0, 1}, {-1, 0, 1}, {-1, 0, -1}};
        }

        if (side <= 3)
        {
            return new int[][] {{0, 1, 0}, {1, 0, 0}, {0, -1, 0}, {-1, 0, 0},
                {1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {-1, 1, 0}};
        }

        return new int[][] {{0, 1, 0}, {0, 0, 1}, {0, -1, 0}, {0, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, -1, -1}, {0, 1, -1}};
    }

    private static boolean same(Object block, Object world, int x, int y, int z) throws Exception
    {
        return getBlock != null
            && getBlock.invoke(world, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z)) == block;
    }

    private static java.util.List<CtmProperties> allRules()
    {
        java.util.List<CtmProperties> all = new java.util.ArrayList<CtmProperties>();

        for (CtmProperties rule : rules.allRules())
        {
            all.add(rule);
        }

        return all;
    }

    private static CtmRuleSet scanPacks()
    {
        File gameDir = Launch.minecraftHome;

        if (gameDir == null)
        {
            return null;
        }

        String[] enabled = enabledPacks(new File(gameDir, "options.txt"));
        return CtmPackScanner.scan(new File(gameDir, "resourcepacks"), enabled, new CtmPackScanner.Problem()
        {
            public void skipped(String source, Exception cause)
            {
                LogWrapper.warning("[Vertex] Skipping CTM file " + source + ": " + cause);
            }
        });
    }

    /** Reads the enabled pack names from options.txt (resourcePacks:["a","b"]). */
    static String[] enabledPacks(File optionsFile)
    {
        try
        {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(optionsFile));

            try
            {
                String line;

                while ((line = reader.readLine()) != null)
                {
                    if (line.startsWith("resourcePacks:"))
                    {
                        java.util.List<String> names = new java.util.ArrayList<String>();
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(line);

                        while (matcher.find())
                        {
                            names.add(matcher.group(1));
                        }

                        return names.toArray(new String[0]);
                    }
                }
            }
            finally
            {
                reader.close();
            }
        }
        catch (Exception unreadable)
        {
        }

        return new String[0];
    }

    private VertexCtm()
    {
    }
}
