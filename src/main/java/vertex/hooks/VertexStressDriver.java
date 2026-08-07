package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Scripted release-stress scenarios, active only with -Dvertex.test.stress=true:
 * a repeating cycle of teleport hops (fast-flight equivalent: forces generation and mass
 * chunk builds), render-distance flips (grid reallocation), churn bursts (mass block
 * updates), a resource reload, and a full world exit/rejoin (teardown and worker
 * survival). -Dvertex.test.quitAfterMs=N ends the run with a clean client shutdown so
 * repeated-launch scripts can assert exit behavior. Every failure disables the driver,
 * never the game.
 */
public final class VertexStressDriver
{
    private static final boolean STRESS = Boolean.getBoolean("vertex.test.stress");
    private static final boolean POISON_TESSELLATOR = Boolean.getBoolean("vertex.test.poisonMainTessellator");
    private static final long QUIT_AFTER_MS = Long.getLong("vertex.test.quitAfterMs", 0L).longValue();

    private static final long[] PHASE_ENDS_MS = {45000L, 75000L, 105000L, 110000L, 115000L};
    private static final String[] PHASE_NAMES = {"teleport", "renderDistance", "massUpdate", "resourceReload", "rejoin"};

    private static boolean disabled = false;
    private static boolean initialized = false;
    private static long startMs = 0L;
    private static long cycleStartMs = 0L;
    private static int phase = -1;
    private static long lastActionMs = 0L;
    private static boolean reloadDone = false;
    private static boolean rejoinDone = false;
    private static final Random random = new Random(19700101L);

    private static Method setPosition;
    private static Method setWorldTime;
    private static Method refreshResources;
    private static Method loadWorld;
    private static Method shutdown;
    private static Field gameSettings;
    private static Field renderDistance;
    private static boolean renderDistanceSaved = false;
    private static int savedRenderDistance;
    private static Field theWorld;
    private static Field thePlayer;

    static boolean active()
    {
        return STRESS && !disabled;
    }

    /** Called from the harness each frame while a world and player exist. */
    static void tick(Object minecraft, Object world, Object player)
    {
        if (!STRESS || disabled)
        {
            return;
        }

        try
        {
            long now = System.currentTimeMillis();

            if (!initialized)
            {
                initialize(minecraft, world, player);
                startMs = now;
                cycleStartMs = now;
                LogWrapper.info("[Vertex] Stress driver armed (cycle: teleport, renderDistance, massUpdate, resourceReload, rejoin)");
            }

            if (QUIT_AFTER_MS > 0L && now - startMs >= QUIT_AFTER_MS)
            {
                LogWrapper.info("[Vertex] Stress driver: quitAfter reached, shutting down cleanly");

                if (renderDistanceSaved)
                {
                    // Undo the render-distance phase before the game persists settings
                    // on shutdown (#115: pinned test values must never outlive the run).
                    renderDistance.setInt(gameSettings.get(minecraft), savedRenderDistance);
                    LogWrapper.info("[Vertex] Stress driver restored render distance " + savedRenderDistance);
                }

                shutdown.invoke(minecraft);
                disabled = true;
                return;
            }

            long inCycle = now - cycleStartMs;
            int currentPhase = phaseFor(inCycle);

            if (currentPhase != phase)
            {
                phase = currentPhase;
                LogWrapper.info("[Vertex] Stress phase: " + PHASE_NAMES[phase]);
            }

            switch (phase)
            {
                case 0:
                    if (now - lastActionMs >= 6000L)
                    {
                        lastActionMs = now;
                        double x = random.nextInt(600) - 300;
                        double z = random.nextInt(600) - 300;
                        setPosition.invoke(player, Double.valueOf(x), Double.valueOf(120.0D), Double.valueOf(z));
                        LogWrapper.info("[Vertex] Stress teleport to " + (int)x + ",120," + (int)z);
                    }

                    break;

                case 1:
                    if (now - lastActionMs >= 15000L)
                    {
                        lastActionMs = now;
                        Object settings = gameSettings.get(minecraft);
                        int current = renderDistance.getInt(settings);
                        int next = current <= 4 ? 10 : 4;
                        renderDistance.setInt(settings, next);
                        LogWrapper.info("[Vertex] Stress render distance " + current + " -> " + next);
                    }

                    break;

                case 2:
                    // Mass block updates: burst promotions well beyond the churn baseline.
                    if (now - lastActionMs >= 500L)
                    {
                        lastActionMs = now;
                        VertexTestHarness.churnBurst(minecraft, 8);
                    }

                    break;

                case 3:
                    if (!reloadDone)
                    {
                        reloadDone = true;
                        LogWrapper.info("[Vertex] Stress resource reload");
                        refreshResources.invoke(minecraft);
                    }

                    break;

                case 4:
                    if (!rejoinDone)
                    {
                        rejoinDone = true;
                        LogWrapper.info("[Vertex] Stress world exit (autoJoin re-enters)");

                        if (POISON_TESSELLATOR)
                        {
                            // Fault injection for #69: begin a draw on the main tessellator
                            // and abandon it, exactly the state a mid-teardown render
                            // failure leaves behind. Without recovery the next rejoin dies
                            // with "Already tesselating!"; with it, a logged recovery.
                            Object tessellator = VertexTessellator.get();
                            Method startQuads = tessellator.getClass().getMethod("b");
                            startQuads.invoke(tessellator);
                            LogWrapper.info("[Vertex] Stress poison: abandoned a main-tessellator draw before exit");
                        }

                        loadWorld.invoke(minecraft, new Object[] {null});
                        // Vanilla's quit-to-title pairs loadWorld(null) with showing the main
                        // menu; omitting the screen leaves a torn GUI whose render NPEs
                        // (found by this suite's first run). Restore the invariant.
                        Class<?> menuClass = Class.forName("bee", true, minecraft.getClass().getClassLoader());
                        Object menu = menuClass.newInstance();
                        Method display = null;

                        for (Method candidate : minecraft.getClass().getMethods())
                        {
                            if (candidate.getName().equals("a") && candidate.getParameterTypes().length == 1
                                && candidate.getParameterTypes()[0].isAssignableFrom(menuClass))
                            {
                                display = candidate;
                                break;
                            }
                        }

                        display.invoke(minecraft, menu);
                        VertexTestHarness.allowRejoin();
                    }

                    break;
            }

            if (inCycle >= PHASE_ENDS_MS[PHASE_ENDS_MS.length - 1])
            {
                cycleStartMs = now;
                phase = -1;
                reloadDone = false;
                rejoinDone = false;
            }
        }
        catch (Throwable e)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Stress driver disabled after failure");
            e.printStackTrace();
        }
    }

    private static int phaseFor(long inCycle)
    {
        for (int i = 0; i < PHASE_ENDS_MS.length; ++i)
        {
            if (inCycle < PHASE_ENDS_MS[i])
            {
                return i;
            }
        }

        return PHASE_ENDS_MS.length - 1;
    }

    private static void initialize(Object minecraft, Object world, Object player) throws Exception
    {
        Class<?> mc = minecraft.getClass();
        Class<?> entity = player.getClass();

        while (entity.getSuperclass() != Object.class)
        {
            entity = entity.getSuperclass();
        }

        setPosition = declared(entity, Mappings.ENTITY_SET_POSITION, double.class, double.class, double.class);
        Class<?> worldBase = world.getClass();

        while (worldBase.getSuperclass() != Object.class)
        {
            worldBase = worldBase.getSuperclass();
        }

        setWorldTime = declared(worldBase, Mappings.WORLD_SET_TIME, long.class);
        refreshResources = mc.getMethod(Mappings.MC_REFRESH_RESOURCES);
        shutdown = mc.getMethod(Mappings.MC_SHUTDOWN);
        loadWorld = declared(mc, Mappings.MC_LOAD_WORLD, world.getClass());
        gameSettings = mc.getDeclaredField(Mappings.MC_GAME_SETTINGS);
        gameSettings.setAccessible(true);
        Object settings = gameSettings.get(minecraft);
        renderDistance = settings.getClass().getDeclaredField(Mappings.GS_RENDER_DISTANCE);
        renderDistance.setAccessible(true);
        // The render-distance phase mutates a persisted setting; snapshot it so a quit
        // mid-cycle cannot leak the stress value into options.txt (#115).
        savedRenderDistance = renderDistance.getInt(settings);
        renderDistanceSaved = true;
        theWorld = mc.getDeclaredField(Mappings.MC_THE_WORLD);
        theWorld.setAccessible(true);
        thePlayer = mc.getDeclaredField(Mappings.MC_THE_PLAYER);
        thePlayer.setAccessible(true);
        initialized = true;
    }

    private static Method declared(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException
    {
        for (Class<?> cls = owner; cls != null && cls != Object.class; cls = cls.getSuperclass())
        {
            try
            {
                Method method = cls.getDeclaredMethod(name, params);
                method.setAccessible(true);
                return method;
            }
            catch (NoSuchMethodException next)
            {
            }
        }

        throw new NoSuchMethodException(name + " in hierarchy of " + owner.getName());
    }

    private VertexStressDriver()
    {
    }
}
