package vertex.benchmark.game;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Creates and controls the disposable benchmark world for all supported clients. */
public final class BenchmarkWorldDriver
{
    private static final long WORLD_SEED = 19700101L;
    private static final long PIN_TIME = 6000L;
    private static final long NANOS_PER_SECOND = 1000000000L;
    private static final long MENU_WARMUP_NANOS = 2000000000L;
    private static final long SETTLE_MILLIS = Long.getLong(
        "vertex.benchmark.settleMillis", 20000L).longValue();
    private static final int ENTITY_COUNT = boundedInteger(
        "vertex.benchmark.entityCount", 160, 16, 512);
    private static final int BLOCK_WIDTH = 12;
    private static final int BLOCK_HEIGHT = 8;
    private static final int BLOCK_COUNT = BLOCK_WIDTH * BLOCK_HEIGHT;
    private static final File CONTROL = controlDirectory();
    private static final FixedRateGate CLIENT_CAMERA_GATE =
        new FixedRateGate(50000000L);
    private static final FixedRateGate CLIENT_ENVIRONMENT_GATE =
        new FixedRateGate(NANOS_PER_SECOND);

    private static volatile boolean failed;
    private static volatile ScenarioPhase activePhase;
    private static volatile long phaseStartedAtNanos;
    private static volatile long serverTicks;

    private static boolean clientInitialized;
    private static boolean worldIssued;
    private static boolean worldInitialized;
    private static boolean worldReady;
    private static boolean stopping;
    private static long menuSeenAtNanos;
    private static long worldSeenAtMillis;
    private static Object clientWorld;
    private static Object clientPlayer;
    private static Object clientSettings;
    private static Field clientWorldField;
    private static Field clientPlayerField;
    private static Field clientSettingsField;
    private static Field hideGuiField;
    private static Field renderDistanceField;
    private static Field clientYawField;
    private static Field clientPitchField;
    private static Field[] clientWeatherFields;
    private static Method launchWorld;
    private static Method shutdown;
    private static Method displayScreen;
    private static Method clientSetPosition;
    private static Method clientSetWorldTime;
    private static Method clientGetSpawn;
    private static Method clientGetHeight;
    private static double anchorX;
    private static double anchorY;
    private static double anchorZ;
    private static ScenarioPhase clientObservedPhase;

    private static boolean serverInitialized;
    private static boolean serverAnchorInitialized;
    private static Method getServerWorld;
    private static Field serverPlayersField;
    private static Field serverPlayerHandlerField;
    private static Field serverEntityXField;
    private static Field serverEntityYField;
    private static Field serverEntityZField;
    private static Field serverEntityRandomField;
    private static Field[] serverWeatherFields;
    private static Method serverTeleport;
    private static Method serverSetWorldTime;
    private static Method serverBlockExists;
    private static Method serverGetBlock;
    private static Method serverGetMetadata;
    private static Method serverSetBlock;
    private static Method serverGetHeight;
    private static Method serverSpawnEntity;
    private static Method serverRemoveEntity;
    private static Method serverSetEntityPosition;
    private static Method serverEnablePersistence;
    private static Method serverGetNavigator;
    private static Method serverMoveNavigator;
    private static Constructor<?> serverPigConstructor;
    private static Object stoneBlock;
    private static Object glowstoneBlock;
    private static double serverAnchorX;
    private static double serverAnchorY;
    private static double serverAnchorZ;
    private static int[] blockX;
    private static int[] blockY;
    private static int[] blockZ;
    private static Object[] originalBlocks;
    private static int[] originalMetadata;
    private static boolean blockPanelActive;
    private static boolean blockPulse;
    private static final List<Object> SERVER_ENTITIES = new ArrayList<Object>();
    private static int entityTargetCursor;

    /** Runs on the client game loop. */
    public static void tick(Object minecraft)
    {
        BenchmarkFrameRecorder.tick();

        if (CONTROL == null)
        {
            return;
        }

        try
        {
            if (!clientInitialized)
            {
                initializeClient(minecraft);
            }

            if (new File(CONTROL, "stop").isFile())
            {
                stop(minecraft);
                return;
            }

            if (failed)
            {
                return;
            }

            long nowNanos = System.nanoTime();
            clientWorld = clientWorldField.get(minecraft);

            if (clientWorld == null)
            {
                if (menuSeenAtNanos == 0L)
                {
                    menuSeenAtNanos = nowNanos;
                }

                if (!worldIssued && nowNanos - menuSeenAtNanos >= MENU_WARMUP_NANOS)
                {
                    worldIssued = true;
                    Object worldSettings = newWorldSettings(
                        minecraft.getClass().getClassLoader());
                    launchWorld.invoke(minecraft, "BenchmarkWorld", "BenchmarkWorld",
                        worldSettings);
                }

                return;
            }

            clientPlayer = clientPlayerField.get(minecraft);

            if (clientPlayer == null)
            {
                return;
            }

            if (!worldInitialized)
            {
                initializeClientWorld(minecraft);

                if (!worldInitialized)
                {
                    return;
                }
            }

            pinClientEnvironment(minecraft, nowNanos);

            if (!worldReady && System.currentTimeMillis() - worldSeenAtMillis >= SETTLE_MILLIS)
            {
                write(new File(CONTROL, "settings.txt"),
                    "scenario=multi-factor-v1\nseed=" + WORLD_SEED
                    + "\nworldType=flat\ntime=" + PIN_TIME
                    + "\nrenderDistance=8\nphases=static,chunks,blocks,entities"
                    + "\nchunkSpeedBlocksPerSecond=24\nblockUpdatesPerSecond="
                    + (BLOCK_COUNT * 20) + "\nentityCount=" + ENTITY_COUNT + "\n");
                write(new File(CONTROL, "ready"), "ready\n");
                worldReady = true;
            }
        }
        catch (Throwable error)
        {
            fail(error);

            try
            {
                stop(minecraft);
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    /** Runs once at the end of each integrated-server tick. */
    public static void serverTick(Object server)
    {
        if (CONTROL == null)
        {
            return;
        }

        try
        {
            ++serverTicks;

            if (serverTicks == 1L || serverTicks % 20L == 0L)
            {
                writeServerTicks();
            }

            if (failed || !new File(CONTROL, "ready").isFile())
            {
                return;
            }

            if (!serverInitialized)
            {
                initializeServer(server);
            }

            Object serverWorld = getServerWorld.invoke(server, Integer.valueOf(0));

            if (serverWorld == null)
            {
                return;
            }

            Object serverPlayer = firstServerPlayer(serverWorld);

            if (serverPlayer == null)
            {
                return;
            }

            if (!serverAnchorInitialized)
            {
                serverAnchorX = serverEntityXField.getDouble(serverPlayer);
                serverAnchorY = serverEntityYField.getDouble(serverPlayer);
                serverAnchorZ = serverEntityZField.getDouble(serverPlayer);
                serverAnchorInitialized = true;
            }

            pinServerEnvironment(serverWorld);
            ScenarioPhase requested = requestedPhase();

            if (requested == null)
            {
                requested = activePhase == null ? ScenarioPhase.STATIC : activePhase;
            }

            if (requested != activePhase)
            {
                activateServerPhase(requested, serverWorld, serverPlayer);
            }

            runServerPhase(serverWorld, serverPlayer, System.nanoTime());
        }
        catch (Throwable error)
        {
            fail(error);
        }
    }

    private static void initializeClient(Object minecraft) throws Exception
    {
        Class<?> owner = minecraft.getClass();
        clientWorldField = accessible(owner, "f");
        clientPlayerField = accessible(owner, "h");
        clientSettingsField = accessible(owner, "u");
        clientSettings = clientSettingsField.get(minecraft);
        hideGuiField = accessible(clientSettings.getClass(), "av");
        renderDistanceField = accessible(clientSettings.getClass(), "c");
        shutdown = owner.getMethod("k");

        for (Method candidate : owner.getMethods())
        {
            Class<?>[] parameters = candidate.getParameterTypes();

            if (candidate.getName().equals("a") && parameters.length == 3
                && parameters[0] == String.class && parameters[1] == String.class)
            {
                launchWorld = candidate;
            }
            else if (candidate.getName().equals("a") && parameters.length == 1
                && parameters[0].getName().equals("bdw"))
            {
                displayScreen = candidate;
            }
        }

        if (launchWorld == null || displayScreen == null)
        {
            throw new IllegalStateException("The 1.7.10 client controls were not found.");
        }

        clientInitialized = true;
    }

    private static Object newWorldSettings(ClassLoader loader) throws Exception
    {
        Class<?> gameType = loader.loadClass("ahk");
        Class<?> worldType = loader.loadClass("ahm");
        Class<?> worldSettings = loader.loadClass("ahj");
        Object creative = gameType.getField("c").get(null);
        Object flat = worldType.getField("c").get(null);
        Constructor<?> constructor = worldSettings.getConstructor(long.class, gameType,
            boolean.class, boolean.class, worldType);
        Object settings = constructor.newInstance(Long.valueOf(WORLD_SEED), creative,
            Boolean.TRUE, Boolean.FALSE, flat);
        return worldSettings.getMethod("b").invoke(settings);
    }

    private static void initializeClientWorld(Object minecraft) throws Exception
    {
        Class<?> entity = rootClass(clientPlayer.getClass());
        clientSetPosition = declared(entity, "b", double.class, double.class, double.class);
        clientYawField = accessible(entity, "y");
        clientPitchField = accessible(entity, "z");
        Class<?> worldBase = rootClass(clientWorld.getClass());
        clientSetWorldTime = declared(worldBase, "b", long.class);
        clientGetSpawn = declared(worldBase, "K");
        clientGetHeight = declared(worldBase, "f", int.class, int.class);
        clientWeatherFields = weatherFields(worldBase);
        Object spawn = clientGetSpawn.invoke(clientWorld);
        int x = accessible(spawn.getClass(), "a").getInt(spawn);
        int z = accessible(spawn.getClass(), "c").getInt(spawn);
        int height = ((Integer)clientGetHeight.invoke(clientWorld, Integer.valueOf(x),
            Integer.valueOf(z))).intValue();

        if (height <= 0)
        {
            clientSetPosition.invoke(clientPlayer, Double.valueOf(x + 0.5D),
                Double.valueOf(120.0D), Double.valueOf(z + 0.5D));
            clientYawField.setFloat(clientPlayer, 45.0F);
            clientPitchField.setFloat(clientPlayer, 10.0F);
            return;
        }

        anchorX = x + 0.5D;
        anchorY = height + 1.62D;
        anchorZ = z + 0.5D;
        worldSeenAtMillis = System.currentTimeMillis();
        CLIENT_CAMERA_GATE.reset(System.nanoTime());
        CLIENT_ENVIRONMENT_GATE.reset(System.nanoTime());
        worldInitialized = true;
    }

    private static void pinClientEnvironment(Object minecraft, long nowNanos)
        throws Exception
    {
        ScenarioPhase phase = activePhase == null ? ScenarioPhase.STATIC : activePhase;

        if (phase != clientObservedPhase)
        {
            clientObservedPhase = phase;
            CLIENT_CAMERA_GATE.reset(nowNanos);
            hideGuiField.setBoolean(clientSettings, true);
            renderDistanceField.setInt(clientSettings, 8);
            displayScreen.invoke(minecraft, new Object[] {null});
        }

        if (CLIENT_ENVIRONMENT_GATE.poll(nowNanos))
        {
            clientSetWorldTime.invoke(clientWorld, Long.valueOf(PIN_TIME));

            for (Field weather : clientWeatherFields)
            {
                weather.setFloat(clientWorld, 0.0F);
            }
        }

        if (!CLIENT_CAMERA_GATE.poll(nowNanos))
        {
            return;
        }

        double x = anchorX;
        double y = anchorY;
        double z = anchorZ;
        float yaw = 45.0F;
        float pitch = 10.0F;

        if (phase == ScenarioPhase.CHUNKS)
        {
            yaw = -90.0F;
            pitch = 25.0F;
        }
        else if (phase == ScenarioPhase.BLOCKS)
        {
            y = anchorY + 4.0D;
            yaw = -90.0F;
            pitch = 5.0F;
        }
        else if (phase == ScenarioPhase.ENTITIES)
        {
            y = anchorY + 8.0D;
            yaw = -90.0F;
            pitch = 12.0F;
        }

        if (phase != ScenarioPhase.CHUNKS)
        {
            clientSetPosition.invoke(clientPlayer, Double.valueOf(x), Double.valueOf(y),
                Double.valueOf(z));
        }

        clientYawField.setFloat(clientPlayer, yaw);
        clientPitchField.setFloat(clientPlayer, pitch);
    }

    private static void initializeServer(Object server) throws Exception
    {
        ClassLoader loader = server.getClass().getClassLoader();
        Class<?> worldBase = loader.loadClass("ahb");
        Class<?> block = loader.loadClass("aji");
        Class<?> entity = loader.loadClass("sa");
        Class<?> living = loader.loadClass("sw");
        Class<?> serverPlayer = loader.loadClass("mw");
        Class<?> handler = loader.loadClass("nh");
        Class<?> navigator = loader.loadClass("vv");
        Class<?> pig = loader.loadClass("wo");
        Class<?> blocks = loader.loadClass("ajn");

        getServerWorld = server.getClass().getMethod("a", int.class);
        serverPlayersField = accessible(worldBase, "h");
        serverPlayerHandlerField = accessible(serverPlayer, "a");
        serverEntityXField = accessible(entity, "s");
        serverEntityYField = accessible(entity, "t");
        serverEntityZField = accessible(entity, "u");
        serverEntityRandomField = accessible(entity, "Z");
        serverWeatherFields = weatherFields(worldBase);
        serverTeleport = handler.getMethod("a", double.class, double.class, double.class,
            float.class, float.class);
        serverSetWorldTime = declared(worldBase, "b", long.class);
        serverBlockExists = declared(worldBase, "d", int.class, int.class, int.class);
        serverGetBlock = declared(worldBase, "a", int.class, int.class, int.class);
        serverGetMetadata = declared(worldBase, "e", int.class, int.class, int.class);
        serverSetBlock = declared(worldBase, "d", int.class, int.class, int.class,
            block, int.class, int.class);
        serverGetHeight = declared(worldBase, "f", int.class, int.class);
        serverSpawnEntity = declared(worldBase, "d", entity);
        serverRemoveEntity = declared(worldBase, "e", entity);
        serverSetEntityPosition = declared(entity, "b", double.class, double.class,
            double.class);
        serverEnablePersistence = declared(living, "bF");
        serverGetNavigator = declared(living, "m");
        serverMoveNavigator = declared(navigator, "a", double.class, double.class,
            double.class, double.class);
        serverPigConstructor = pig.getConstructor(worldBase);
        stoneBlock = blocks.getField("b").get(null);
        glowstoneBlock = blocks.getField("aN").get(null);
        serverInitialized = true;
    }

    private static Object firstServerPlayer(Object serverWorld) throws Exception
    {
        List<?> players = (List<?>)serverPlayersField.get(serverWorld);
        return players == null || players.isEmpty() ? null : players.get(0);
    }

    private static void pinServerEnvironment(Object serverWorld) throws Exception
    {
        if (serverTicks % 20L != 0L)
        {
            return;
        }

        serverSetWorldTime.invoke(serverWorld, Long.valueOf(PIN_TIME));

        for (Field weather : serverWeatherFields)
        {
            weather.setFloat(serverWorld, 0.0F);
        }
    }

    private static void activateServerPhase(ScenarioPhase phase, Object serverWorld,
        Object serverPlayer) throws Exception
    {
        cleanupServerPhase(activePhase, serverWorld, serverPlayer);
        activePhase = phase;
        blockPulse = false;
        entityTargetCursor = 0;

        if (phase == ScenarioPhase.CHUNKS)
        {
            teleportServerPlayer(serverPlayer, serverAnchorX, serverAnchorY + 40.0D,
                serverAnchorZ, -90.0F, 25.0F);
        }
        else if (phase == ScenarioPhase.BLOCKS)
        {
            setupBlockPanel(serverWorld);
        }
        else if (phase == ScenarioPhase.ENTITIES)
        {
            setupEntities(serverWorld);
        }

        phaseStartedAtNanos = System.nanoTime();
        writeServerTicks();
        write(new File(CONTROL, "ready-" + phase.getId()),
            "phase=" + phase.getId() + "\nserverTick=" + serverTicks
            + "\nentityCount=" + (phase == ScenarioPhase.ENTITIES ? ENTITY_COUNT : 0)
            + "\nblockCount=" + (phase == ScenarioPhase.BLOCKS ? BLOCK_COUNT : 0)
            + "\n");
    }

    private static void runServerPhase(Object serverWorld, Object serverPlayer,
        long nowNanos) throws Exception
    {
        long elapsed = Math.max(0L, nowNanos - phaseStartedAtNanos);

        if (activePhase == ScenarioPhase.CHUNKS)
        {
            teleportServerPlayer(serverPlayer,
                ScenarioMotion.chunkX(serverAnchorX, elapsed), serverAnchorY + 40.0D,
                ScenarioMotion.chunkZ(serverAnchorZ, elapsed), -90.0F, 25.0F);
        }
        else if (activePhase == ScenarioPhase.BLOCKS)
        {
            updateBlockPanel(serverWorld);
        }
        else if (activePhase == ScenarioPhase.ENTITIES)
        {
            updateEntityTargets(serverWorld, elapsed);
        }
    }

    private static void teleportServerPlayer(Object serverPlayer, double x, double y,
        double z, float yaw, float pitch) throws Exception
    {
        Object handler = serverPlayerHandlerField.get(serverPlayer);

        if (handler == null)
        {
            throw new IllegalStateException("The benchmark player connection is not ready.");
        }

        serverTeleport.invoke(handler, Double.valueOf(x), Double.valueOf(y),
            Double.valueOf(z), Float.valueOf(yaw), Float.valueOf(pitch));
    }

    private static void setupBlockPanel(Object serverWorld) throws Exception
    {
        blockX = new int[BLOCK_COUNT];
        blockY = new int[BLOCK_COUNT];
        blockZ = new int[BLOCK_COUNT];
        originalBlocks = new Object[BLOCK_COUNT];
        originalMetadata = new int[BLOCK_COUNT];
        int baseX = floor(serverAnchorX) + 12;
        int baseY = Math.max(2, Math.min(246, floor(serverAnchorY) - 2));
        int baseZ = floor(serverAnchorZ) - BLOCK_WIDTH / 2;

        for (int row = 0; row < BLOCK_HEIGHT; ++row)
        {
            for (int column = 0; column < BLOCK_WIDTH; ++column)
            {
                int index = row * BLOCK_WIDTH + column;
                blockX[index] = baseX;
                blockY[index] = baseY + row;
                blockZ[index] = baseZ + column;

                if (!((Boolean)serverBlockExists.invoke(serverWorld,
                    Integer.valueOf(blockX[index]), Integer.valueOf(blockY[index]),
                    Integer.valueOf(blockZ[index]))).booleanValue())
                {
                    throw new IllegalStateException("The block workload area is not loaded.");
                }

                originalBlocks[index] = serverGetBlock.invoke(serverWorld,
                    Integer.valueOf(blockX[index]), Integer.valueOf(blockY[index]),
                    Integer.valueOf(blockZ[index]));
                originalMetadata[index] = ((Integer)serverGetMetadata.invoke(serverWorld,
                    Integer.valueOf(blockX[index]), Integer.valueOf(blockY[index]),
                    Integer.valueOf(blockZ[index]))).intValue();
                setServerBlock(serverWorld, index, stoneBlock, 0);
            }
        }

        blockPanelActive = true;
    }

    private static void updateBlockPanel(Object serverWorld) throws Exception
    {
        blockPulse = !blockPulse;

        for (int index = 0; index < BLOCK_COUNT; ++index)
        {
            boolean light = ((index + (blockPulse ? 1 : 0)) & 1) == 0;
            setServerBlock(serverWorld, index, light ? glowstoneBlock : stoneBlock, 0);
        }
    }

    private static void setServerBlock(Object serverWorld, int index, Object block,
        int metadata) throws Exception
    {
        serverSetBlock.invoke(serverWorld, Integer.valueOf(blockX[index]),
            Integer.valueOf(blockY[index]), Integer.valueOf(blockZ[index]), block,
            Integer.valueOf(metadata), Integer.valueOf(3));
    }

    private static void setupEntities(Object serverWorld) throws Exception
    {
        for (int index = 0; index < ENTITY_COUNT; ++index)
        {
            double x = ScenarioMotion.entityX(serverAnchorX, index, ENTITY_COUNT, 0L);
            double z = ScenarioMotion.entityZ(serverAnchorZ, index, ENTITY_COUNT, 0L);
            int y = ((Integer)serverGetHeight.invoke(serverWorld, Integer.valueOf(floor(x)),
                Integer.valueOf(floor(z)))).intValue();
            Object entity = serverPigConstructor.newInstance(serverWorld);
            serverEntityRandomField.set(entity, new Random(WORLD_SEED + index));
            serverSetEntityPosition.invoke(entity, Double.valueOf(x),
                Double.valueOf(y + 1.0D), Double.valueOf(z));
            serverEnablePersistence.invoke(entity);
            boolean spawned = ((Boolean)serverSpawnEntity.invoke(serverWorld, entity))
                .booleanValue();

            if (!spawned)
            {
                throw new IllegalStateException("The entity workload did not reach its target.");
            }

            SERVER_ENTITIES.add(entity);
        }

        if (SERVER_ENTITIES.size() != ENTITY_COUNT)
        {
            throw new IllegalStateException("The entity workload did not reach its target.");
        }
    }

    private static void updateEntityTargets(Object serverWorld, long elapsedNanos)
        throws Exception
    {
        int updates = Math.min(8, SERVER_ENTITIES.size());

        for (int offset = 0; offset < updates; ++offset)
        {
            int index = (entityTargetCursor + offset) % SERVER_ENTITIES.size();
            Object entity = SERVER_ENTITIES.get(index);
            double x = ScenarioMotion.entityX(serverAnchorX, index, ENTITY_COUNT,
                elapsedNanos);
            double z = ScenarioMotion.entityZ(serverAnchorZ, index, ENTITY_COUNT,
                elapsedNanos);
            int y = ((Integer)serverGetHeight.invoke(serverWorld, Integer.valueOf(floor(x)),
                Integer.valueOf(floor(z)))).intValue();
            Object navigator = serverGetNavigator.invoke(entity);
            serverMoveNavigator.invoke(navigator, Double.valueOf(x),
                Double.valueOf(y + 1.0D), Double.valueOf(z), Double.valueOf(1.0D));
        }

        if (!SERVER_ENTITIES.isEmpty())
        {
            entityTargetCursor = (entityTargetCursor + updates) % SERVER_ENTITIES.size();
        }
    }

    private static void cleanupServerPhase(ScenarioPhase phase, Object serverWorld,
        Object serverPlayer) throws Exception
    {
        if (phase == ScenarioPhase.CHUNKS && serverAnchorInitialized)
        {
            teleportServerPlayer(serverPlayer, serverAnchorX, serverAnchorY, serverAnchorZ,
                45.0F, 10.0F);
        }

        if (blockPanelActive)
        {
            for (int index = 0; index < BLOCK_COUNT; ++index)
            {
                setServerBlock(serverWorld, index, originalBlocks[index],
                    originalMetadata[index]);
            }

            blockPanelActive = false;
        }

        if (!SERVER_ENTITIES.isEmpty())
        {
            for (Object entity : SERVER_ENTITIES)
            {
                serverRemoveEntity.invoke(serverWorld, entity);
            }

            SERVER_ENTITIES.clear();
        }
    }

    private static ScenarioPhase requestedPhase()
    {
        String fixed = System.getProperty("vertex.benchmark.phase");

        if (fixed != null && !fixed.trim().isEmpty())
        {
            return ScenarioPhase.fromId(fixed);
        }

        ScenarioPhase result = null;

        for (ScenarioPhase phase : ScenarioPhase.values())
        {
            if (new File(CONTROL, "phase-" + phase.getId()).isFile())
            {
                if (result != null)
                {
                    throw new IllegalStateException(
                        "Only one benchmark phase marker is permitted.");
                }

                result = phase;
            }
        }

        return result;
    }

    private static void stop(Object minecraft) throws Exception
    {
        if (!stopping)
        {
            stopping = true;
            write(new File(CONTROL, "stopping"), "stopping\n");
            shutdown.invoke(minecraft);
        }
    }

    private static void fail(Throwable error)
    {
        failed = true;
        String message = error.getMessage();
        write(new File(CONTROL, "failed.txt"), error.getClass().getName() + ": "
            + (message == null ? "The scenario failed." : message) + "\n");
    }

    private static void writeServerTicks()
    {
        write(new File(CONTROL, "server-ticks.txt"), Long.toString(serverTicks) + "\n");
    }

    private static File controlDirectory()
    {
        String path = System.getProperty("vertex.benchmark.controlDir");
        return path == null || path.trim().isEmpty() ? null : new File(path);
    }

    private static void write(File file, String value)
    {
        FileOutputStream output = null;

        try
        {
            CONTROL.mkdirs();
            output = new FileOutputStream(file);
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception ignored)
        {
        }
        finally
        {
            if (output != null)
            {
                try
                {
                    output.close();
                }
                catch (Exception ignored)
                {
                }
            }
        }
    }

    private static Field[] weatherFields(Class<?> worldBase) throws Exception
    {
        Field[] fields = new Field[4];
        String[] names = {"m", "n", "o", "p"};

        for (int index = 0; index < names.length; ++index)
        {
            fields[index] = accessible(worldBase, names[index]);
        }

        return fields;
    }

    private static Class<?> rootClass(Class<?> value)
    {
        Class<?> result = value;

        while (result.getSuperclass() != null && result.getSuperclass() != Object.class)
        {
            result = result.getSuperclass();
        }

        return result;
    }

    private static Field accessible(Class<?> owner, String name) throws Exception
    {
        for (Class<?> type = owner; type != null && type != Object.class;
            type = type.getSuperclass())
        {
            try
            {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException absent)
            {
            }
        }

        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Method declared(Class<?> owner, String name, Class<?>... parameters)
        throws Exception
    {
        for (Class<?> type = owner; type != null && type != Object.class;
            type = type.getSuperclass())
        {
            try
            {
                Method method = type.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            }
            catch (NoSuchMethodException absent)
            {
            }
        }

        throw new NoSuchMethodException(owner.getName() + "." + name);
    }

    private static int floor(double value)
    {
        int truncated = (int)value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static int boundedInteger(String key, int fallback, int minimum, int maximum)
    {
        Integer value = Integer.getInteger(key);
        int result = value == null ? fallback : value.intValue();
        return Math.max(minimum, Math.min(maximum, result));
    }

    private BenchmarkWorldDriver()
    {
    }
}
