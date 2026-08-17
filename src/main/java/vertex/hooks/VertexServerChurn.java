package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Server-side world mutation and save driver for the test fixture (issue #195).
 *
 * The existing {@code -Dvertex.test.churn} driver promotes render sections on the
 * client; it never writes a block, so server chunks stay clean, nothing schedules a
 * tick, and no chunk is ever written back. Anything on the save/load path - the
 * scheduled-tick index, chunk serialization, pending-update queries - was therefore
 * unreachable from the harness.
 *
 * Two opt-in properties close that gap:
 *
 *   -Dvertex.test.blockChurn=N   toggle ~N blocks per second in the integrated server's
 *                                world, marking their chunks dirty
 *   -Dvertex.test.saveEvery=S    call MinecraftServer.saveAllWorlds every S seconds
 *
 * The save runs from the client tick while log4j is still alive, which the fixture's
 * SIGTERM shutdown cannot do: log4j tears its appenders down first, so the vanilla
 * shutdown save dies on "Attempted to append to non-started appender" before it reaches
 * saveAllWorlds.
 *
 * Mutations are deliberately inert: a single column high above terrain alternates
 * between stone and air, so a chunk is dirtied and its neighbours notified without
 * disturbing the fixture world's surface, and an even number of toggles leaves the
 * column exactly as it was found.
 */
public final class VertexServerChurn
{
    private static final int BLOCK_CHURN = Integer.getInteger("vertex.test.blockChurn", 0).intValue();
    private static final int SAVE_EVERY = Integer.getInteger("vertex.test.saveEvery", 0).intValue();
    /** Well above any 1.7.10 terrain, below the 256 build limit. */
    private static final int CHURN_Y = 200;

    private static boolean disabled = false;
    private static boolean resolved = false;
    private static long lastChurnMs = 0L;
    private static long lastSaveMs = 0L;
    private static long toggles = 0L;
    private static long saves = 0L;
    private static boolean placing = true;

    private static Method getIntegratedServer;
    private static Field worldServers;
    private static Method saveAllWorlds;
    private static Method setBlock;
    private static Object airBlock;
    private static Object stoneBlock;
    private static Field playerPosX;
    private static Field playerPosZ;
    private static Field thePlayer;

    public static boolean active()
    {
        return !disabled && (BLOCK_CHURN > 0 || SAVE_EVERY > 0);
    }

    /** Ticked from the harness; both drivers are wall-clock paced. */
    public static void tick(Object minecraft)
    {
        if (!active())
        {
            return;
        }

        try
        {
            if (!resolved)
            {
                resolve(minecraft);
            }

            Object server = getIntegratedServer.invoke(minecraft);

            if (server == null)
            {
                return;
            }

            Object[] worlds = (Object[])worldServers.get(server);

            if (worlds == null || worlds.length == 0 || worlds[0] == null)
            {
                return;
            }

            long now = System.currentTimeMillis();

            if (BLOCK_CHURN > 0 && now - lastChurnMs >= 1000L / BLOCK_CHURN)
            {
                lastChurnMs = now;
                churnBlock(minecraft, worlds[0]);
            }

            if (SAVE_EVERY > 0 && now - lastSaveMs >= SAVE_EVERY * 1000L)
            {
                if (lastSaveMs != 0L)
                {
                    saveAllWorlds.invoke(server, Boolean.FALSE);
                    ++saves;
                    LogWrapper.info("[Vertex] Test save #" + saves + " complete (blockToggles=" + toggles + ")");
                }

                lastSaveMs = now;
            }
        }
        catch (Throwable t)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Server churn/save driver disabled after failure");
            t.printStackTrace();
        }
    }

    /**
     * Alternates one high-altitude column between stone and air. Flag 3 = update clients
     * and notify neighbours, so the write follows the same path a player edit does.
     */
    private static void churnBlock(Object minecraft, Object world) throws Exception
    {
        Object player = thePlayer.get(minecraft);

        if (player == null)
        {
            return;
        }

        int x = (int)playerPosX.getDouble(player) + (int)(toggles % 8L) - 4;
        int z = (int)playerPosZ.getDouble(player) + (int)((toggles / 8L) % 8L) - 4;
        Object block = placing ? stoneBlock : airBlock;

        setBlock.invoke(world, Integer.valueOf(x), Integer.valueOf(CHURN_Y), Integer.valueOf(z),
            block, Integer.valueOf(0), Integer.valueOf(3));
        ++toggles;

        // Alternate only after a full 8x8 sweep so each column returns to air.
        if (toggles % 64L == 0L)
        {
            placing = !placing;
        }
    }

    private static void resolve(Object minecraft) throws Exception
    {
        getIntegratedServer = minecraft.getClass().getMethod(Mappings.MC_GET_INTEGRATED_SERVER);
        getIntegratedServer.setAccessible(true);
        Class<?> serverClass = getIntegratedServer.getReturnType();

        while (serverClass.getSuperclass() != Object.class && !hasField(serverClass, Mappings.MS_WORLD_SERVERS))
        {
            serverClass = serverClass.getSuperclass();
        }

        worldServers = serverClass.getDeclaredField(Mappings.MS_WORLD_SERVERS);
        worldServers.setAccessible(true);
        saveAllWorlds = serverClass.getDeclaredMethod(Mappings.MS_SAVE_ALL_WORLDS, boolean.class);
        saveAllWorlds.setAccessible(true);

        thePlayer = minecraft.getClass().getDeclaredField(Mappings.MC_THE_PLAYER);
        thePlayer.setAccessible(true);
        Class<?> entityRoot = thePlayer.getType();

        while (entityRoot.getSuperclass() != Object.class)
        {
            entityRoot = entityRoot.getSuperclass();
        }

        playerPosX = entityRoot.getDeclaredField(Mappings.ENTITY_POS_X);
        playerPosX.setAccessible(true);
        playerPosZ = entityRoot.getDeclaredField(Mappings.ENTITY_POS_Z);
        playerPosZ.setAccessible(true);

        Class<?> blocks = net.minecraft.launchwrapper.Launch.classLoader
            .loadClass(Mappings.BLOCKS_REGISTRY);
        Class<?> blockClass = net.minecraft.launchwrapper.Launch.classLoader
            .loadClass(Mappings.BLOCK);
        airBlock = field(blocks, Mappings.BLOCKS_AIR).get(null);
        stoneBlock = field(blocks, Mappings.BLOCKS_STONE).get(null);

        Class<?> worldRoot = net.minecraft.launchwrapper.Launch.classLoader
            .loadClass(Mappings.WORLD_CLASS);
        setBlock = worldRoot.getDeclaredMethod(Mappings.WORLD_SET_BLOCK, int.class, int.class,
            int.class, blockClass, int.class, int.class);
        setBlock.setAccessible(true);

        resolved = true;
        LogWrapper.info("[Vertex] Server churn armed (blockChurn=" + BLOCK_CHURN
            + "/s, saveEvery=" + SAVE_EVERY + "s)");
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException
    {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static boolean hasField(Class<?> type, String name)
    {
        try
        {
            type.getDeclaredField(name);
            return true;
        }
        catch (NoSuchFieldException absent)
        {
            return false;
        }
    }

    /** Diagnostics counters. */
    public static long blockToggles()
    {
        return toggles;
    }

    public static long saveCount()
    {
        return saves;
    }

    private VertexServerChurn()
    {
    }
}
