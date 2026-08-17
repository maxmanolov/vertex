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
 * Both operations run from the tail of MinecraftServer.tick, on the integrated-server
 * thread while log4j is still alive. The fixture's SIGTERM shutdown cannot save: log4j
 * tears its appenders down first, so the vanilla shutdown dies on "Attempted to append
 * to non-started appender" before it reaches saveAllWorlds.
 *
 * Mutations use an 8x8 plane above the fixture terrain. A complete place/remove pair of
 * sweeps restores it to air; an interrupted run can leave part of the plane populated,
 * which is why this driver is restricted to scratch world copies.
 */
public final class VertexServerChurn
{
    private static final int BLOCK_CHURN = Integer.getInteger("vertex.test.blockChurn", 0).intValue();
    private static final int SAVE_EVERY = Integer.getInteger("vertex.test.saveEvery", 0).intValue();
    public static final boolean ACTIVE = BLOCK_CHURN > 0 || SAVE_EVERY > 0;
    /** Above the verified fixture surface and below the 256 build limit. */
    private static final int CHURN_Y = 200;

    private static boolean disabled = false;
    private static boolean serverResolved = false;
    private static boolean worldResolved = false;
    private static long lastChurnMs = 0L;
    private static long lastSaveMs = 0L;
    private static long toggles = 0L;
    private static long saves = 0L;
    private static boolean placing = true;

    private static Field worldServers;
    private static Method saveAllWorlds;
    private static Method setBlock;
    private static Object airBlock;
    private static Object stoneBlock;
    private static Method getSpawnPoint;
    private static int originX;
    private static int originZ;

    public static boolean active()
    {
        return ACTIVE && !disabled;
    }

    /** Ticked at the server-tick tail; both drivers are wall-clock paced. */
    public static void tick(Object server)
    {
        if (!active())
        {
            return;
        }

        try
        {
            if (!serverResolved)
            {
                resolveServer(server);
            }

            Object[] worlds = (Object[])worldServers.get(server);

            if (worlds == null || worlds.length == 0 || worlds[0] == null)
            {
                return;
            }

            if (!worldResolved)
            {
                resolveWorld(server, worlds[0]);
            }

            long now = System.currentTimeMillis();

            if (BLOCK_CHURN > 0 && now - lastChurnMs >= 1000L / BLOCK_CHURN)
            {
                lastChurnMs = now;
                churnBlock(worlds[0]);
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
     * Alternates an 8x8 high-altitude plane between stone and air. Flag 3 = update clients
     * and notify neighbours, so the write follows the same path a player edit does.
     */
    private static void churnBlock(Object world) throws Exception
    {
        int x = originX + (int)(toggles % 8L) - 4;
        int z = originZ + (int)((toggles / 8L) % 8L) - 4;
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

    private static void resolveServer(Object server) throws Exception
    {
        Class<?> serverClass = server.getClass();

        while (serverClass.getSuperclass() != Object.class && !hasField(serverClass, Mappings.MS_WORLD_SERVERS))
        {
            serverClass = serverClass.getSuperclass();
        }

        worldServers = serverClass.getDeclaredField(Mappings.MS_WORLD_SERVERS);
        worldServers.setAccessible(true);
        saveAllWorlds = serverClass.getDeclaredMethod(Mappings.MS_SAVE_ALL_WORLDS, boolean.class);
        saveAllWorlds.setAccessible(true);
        serverResolved = true;
    }

    private static void resolveWorld(Object server, Object world) throws Exception
    {
        ClassLoader loader = server.getClass().getClassLoader();
        Class<?> blocks = loader
            .loadClass(Mappings.BLOCKS_REGISTRY);
        Field air = field(blocks, Mappings.BLOCKS_AIR);
        Field stone = field(blocks, Mappings.BLOCKS_STONE);
        Class<?> blockClass = stone.getType();
        airBlock = air.get(null);
        stoneBlock = stone.get(null);

        Class<?> worldRoot = loader
            .loadClass(Mappings.WORLD_CLASS);
        setBlock = worldRoot.getDeclaredMethod(Mappings.WORLD_SET_BLOCK, int.class, int.class,
            int.class, blockClass, int.class, int.class);
        setBlock.setAccessible(true);
        getSpawnPoint = worldRoot.getMethod(Mappings.WORLD_GET_SPAWN_POINT);
        getSpawnPoint.setAccessible(true);
        Object spawn = getSpawnPoint.invoke(world);
        originX = field(spawn.getClass(), Mappings.COORD_X).getInt(spawn);
        originZ = field(spawn.getClass(), Mappings.COORD_Z).getInt(spawn);

        worldResolved = true;
        LogWrapper.info("[Vertex] Server churn armed (blockChurn=" + BLOCK_CHURN
            + "/s, saveEvery=" + SAVE_EVERY + "s, origin=" + originX + "," + originZ + ")");
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
