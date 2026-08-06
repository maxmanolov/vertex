package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Autonomous in-world regression harness, activated only by system properties so normal
 * players never execute more than one null check per frame:
 *
 *   -Dvertex.test.autoJoin=WORLDNAME   join the named singleplayer world once the menu is up
 *   -Dvertex.test.churn=N              force-promote ~N chunk sections per second near the
 *                                      player, generating sustained rebuild load
 *
 * Together these turn the merge gates' mechanical half ("survives X minutes in-world with
 * zero self-disables under rebuild load") into a scriptable soak test: launch with both
 * flags, watch the log for join + diagnostics lines, assert the process outlives the soak
 * window. Ticked from the head of Minecraft.runGameLoop.
 */
public final class VertexTestHarness
{
    private static final String AUTO_JOIN = System.getProperty("vertex.test.autoJoin");
    private static final int CHURN = Integer.getInteger("vertex.test.churn", 0).intValue();
    private static final int MENU_WARMUP_FRAMES = 200;

    private static boolean disabled = false;
    private static boolean initialized = false;
    private static boolean joinIssued = false;
    private static int menuFrames = 0;
    private static long lastChurnMs = 0L;
    private static final java.util.Random random = new java.util.Random(20260805L);

    private static Method launchIntegratedServer;
    private static Method getHealth;
    private static Method respawnPlayer;
    private static Field theWorld;
    private static Field thePlayer;
    private static Field renderGlobal;
    private static Field posX;
    private static Field posY;
    private static Field posZ;

    public static void tick(Object minecraft)
    {
        VertexDynamicLightsCollector.tick(minecraft);
        VertexPackLoader.tick(minecraft);

        if (disabled || (AUTO_JOIN == null && CHURN <= 0))
        {
            return;
        }

        try
        {
            if (!initialized)
            {
                initialize(minecraft);
            }

            Object world = theWorld.get(minecraft);

            if (AUTO_JOIN != null && !joinIssued && world == null)
            {
                if (++menuFrames >= MENU_WARMUP_FRAMES)
                {
                    joinIssued = true;
                    LogWrapper.info("[Vertex] Test harness: joining world '" + AUTO_JOIN + "'");
                    launchIntegratedServer.invoke(minecraft, AUTO_JOIN, AUTO_JOIN, null);
                }

                return;
            }

            if (world != null && (VertexStressDriver.active() || VertexFrameCapture.active()))
            {
                Object scriptPlayer = thePlayer.get(minecraft);

                if (scriptPlayer != null)
                {
                    // Scripted runs must survive any death (saved corpse state, lava
                    // teleport): respawn immediately so the scenario keeps its camera.
                    if (getHealth == null)
                    {
                        getHealth = method(scriptPlayer.getClass(), "aS");
                        respawnPlayer = method(scriptPlayer.getClass(), "bH");
                    }

                    if (((Float)getHealth.invoke(scriptPlayer)).floatValue() <= 0.0F)
                    {
                        LogWrapper.info("[Vertex] Test harness: dead player detected, respawning");
                        respawnPlayer.invoke(scriptPlayer);
                        return;
                    }

                    if (VertexStressDriver.active())
                    {
                        VertexStressDriver.tick(minecraft, world, scriptPlayer);
                    }

                    if (VertexFrameCapture.active())
                    {
                        VertexFrameCapture.tick(minecraft, world, scriptPlayer);
                    }
                }
            }

            if (CHURN > 0 && world != null)
            {
                long now = System.currentTimeMillis();

                if (now - lastChurnMs >= 1000L / CHURN)
                {
                    lastChurnMs = now;
                    Object player = thePlayer.get(minecraft);
                    Object rg = renderGlobal.get(minecraft);

                    if (player != null && rg != null)
                    {
                        int x = (int)posX.getDouble(player) + random.nextInt(33) - 16;
                        int y = Math.max(1, Math.min(254, (int)posY.getDouble(player) + random.nextInt(17) - 8));
                        int z = (int)posZ.getDouble(player) + random.nextInt(33) - 16;
                        VertexHooks.promoteForTest(rg, x, y, z);
                    }
                }
            }
        }
        catch (Exception e)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Test harness disabled after failure");
            e.printStackTrace();
        }
    }

    /** Stress driver: world exit happened; let autoJoin re-enter from the menu. */
    static void allowRejoin()
    {
        joinIssued = false;
        menuFrames = 0;
    }

    /** Stress driver: one churn burst of n promotions around the player, right now. */
    static void churnBurst(Object minecraft, int count) throws Exception
    {
        Object player = thePlayer.get(minecraft);
        Object rg = renderGlobal.get(minecraft);

        if (player == null || rg == null)
        {
            return;
        }

        for (int i = 0; i < count; ++i)
        {
            int x = (int)posX.getDouble(player) + random.nextInt(33) - 16;
            int y = Math.max(1, Math.min(254, (int)posY.getDouble(player) + random.nextInt(17) - 8));
            int z = (int)posZ.getDouble(player) + random.nextInt(33) - 16;
            VertexHooks.promoteForTest(rg, x, y, z);
        }
    }

    private static Method method(Class<?> owner, String name) throws NoSuchMethodException
    {
        for (Class<?> cls = owner; cls != null && cls != Object.class; cls = cls.getSuperclass())
        {
            for (Method candidate : cls.getDeclaredMethods())
            {
                if (candidate.getName().equals(name) && candidate.getParameterTypes().length == 0)
                {
                    candidate.setAccessible(true);
                    return candidate;
                }
            }
        }

        throw new NoSuchMethodException(name + " in hierarchy of " + owner.getName());
    }

    private static void initialize(Object minecraft) throws Exception
    {
        Class<?> mc = minecraft.getClass();
        theWorld = accessible(mc, Mappings.MC_THE_WORLD);
        thePlayer = accessible(mc, Mappings.MC_THE_PLAYER);
        renderGlobal = accessible(mc, Mappings.MC_RENDER_GLOBAL);

        for (Method candidate : mc.getMethods())
        {
            if (candidate.getName().equals(Mappings.MC_LAUNCH_INTEGRATED_SERVER) && candidate.getParameterTypes().length == 3
                && candidate.getParameterTypes()[0] == String.class && candidate.getParameterTypes()[1] == String.class)
            {
                launchIntegratedServer = candidate;
            }
        }

        if (launchIntegratedServer == null)
        {
            throw new IllegalStateException("launchIntegratedServer not found");
        }

        // Player position fields live on the root entity class, directly under Object.
        Class<?> entity = thePlayer.getType();

        while (entity.getSuperclass() != Object.class)
        {
            entity = entity.getSuperclass();
        }

        posX = accessible(entity, Mappings.ENTITY_POS_X);
        posY = accessible(entity, Mappings.ENTITY_POS_Y);
        posZ = accessible(entity, Mappings.ENTITY_POS_Z);
        initialized = true;
        LogWrapper.info("[Vertex] Test harness armed (autoJoin=" + AUTO_JOIN + ", churn=" + CHURN + "/s)");
    }

    private static Field accessible(Class<?> owner, String name) throws NoSuchFieldException
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private VertexTestHarness()
    {
    }
}
