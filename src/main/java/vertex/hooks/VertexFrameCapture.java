package vertex.hooks;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import vertex.Mappings;

/**
 * Deterministic framebuffer captures for automated visual comparison, active only with
 * -Dvertex.test.shotDir=path. After the world joins and settles, the player is pinned to
 * a fixed position and camera, world time is pinned every frame, the GUI is hidden, and
 * three angles are captured from the front buffer (the last fully presented frame) as
 * both PNG (for humans) and raw RGB (for exact byte comparison between runs).
 *
 * Two runs of the same world and config that differ only in -Dvertex.multicore therefore
 * produce comparable images: geometry differences between the vanilla and worker build
 * paths show up as pixel diffs. Residual nondeterminism (mobs that wandered into frame)
 * is quantified by the comparison script rather than hidden.
 */
public final class VertexFrameCapture
{
    private static final String SHOT_DIR = System.getProperty("vertex.test.shotDir");
    private static final boolean MOTION = Boolean.getBoolean("vertex.test.motion");
    // HUD comparisons (chat/scoreboard backgrounds) need the GUI visible in shots.
    private static final boolean SHOW_HUD = Boolean.getBoolean("vertex.test.showHud");
    private static final int MOTION_SHOTS = 24;
    private static final int MOTION_FRAME_STRIDE = 40;
    private static final double MOTION_SPEED = 0.25D;
    private static int motionFrames = 0;
    private static int motionCaptured = 0;
    private static double motionZ = 0.0D;
    private static final java.util.List<byte[]> motionRaws = new java.util.ArrayList<byte[]>();
    private static final long SETTLE_MS = 45000L;
    private static final int FRAMES_PER_ANGLE = 120;
    private static final float[] YAWS = {0.0F, 120.0F, 240.0F};




    private static boolean disabled = false;
    private static boolean initialized = false;
    private static boolean done = false;
    private static long worldSeenMs = 0L;
    private static int angleIndex = 0;
    private static int settleFrames = 0;

    private static Method setPosition;
    private static Method setWorldTime;
    private static Method getHeightValue;
    private static Method getSpawnPoint;
    private static Method displayGuiScreen;
    private static Method getIntegratedServer;
    private static Field worldServers;
    private static Field[] rainFields;
    private static double groundY = -1.0D;
    private static double anchorX;
    private static double anchorZ;
    private static boolean anchored = false;
    private static Field rotationYaw;
    private static Field rotationPitch;
    private static Field gameSettings;
    private static Field hideGui;
    private static Field difficulty;
    private static Field posXField;
    private static Field posZField;
    private static boolean settingsSnapshotTaken = false;
    private static boolean savedHideGui;
    private static Object savedDifficulty;
    private static Field renderGlobalField;
    private static Field renderDistanceField;
    private static int drainedFrames = 0;

    static boolean active()
    {
        return SHOT_DIR != null && !disabled && !done;
    }

    static void tick(Object minecraft, Object world, Object player)
    {
        if (!active())
        {
            return;
        }

        try
        {
            long now = System.currentTimeMillis();

            if (!initialized)
            {
                initialize(minecraft, world, player);
                LogWrapper.info("[Vertex] Frame capture armed: " + SHOT_DIR);
            }

            if (worldSeenMs == 0L)
            {
                worldSeenMs = now;
            }

            // Environment pins run EVERY tick unconditionally - an early return before
            // these once let night fall, and hostile mobs killed the spawn-idling player
            // 34 times in a single run. Peaceful difficulty removes hostiles entirely.
            setWorldTime.invoke(world, Long.valueOf(6000L));

            // Weather strength scales the whole terrain lightmap even with rain rendering
            // off (the diff mask showed every terrain pixel shifted while sky matched:
            // brightness, not geometry). Pin all four interpolation fields to clear.
            if (rainFields == null)
            {
                Class<?> worldBase2 = world.getClass();

                while (worldBase2.getSuperclass() != Object.class)
                {
                    worldBase2 = worldBase2.getSuperclass();
                }

                rainFields = new Field[4];
                String[] names = {"m", "n", "o", "p"};

                for (int i = 0; i < 4; ++i)
                {
                    rainFields[i] = worldBase2.getDeclaredField(names[i]);
                    rainFields[i].setAccessible(true);
                }
            }

            for (Field rainField : rainFields)
            {
                rainField.setFloat(world, 0.0F);
            }

            // The integrated server re-syncs its own clock to the client every few
            // seconds, overwriting the client pin (observed as noon/night swings between
            // captures). Pin the authoritative server world too.
            Object server = getIntegratedServer.invoke(minecraft);

            if (server != null)
            {
                Object[] serverWorlds = (Object[])worldServers.get(server);

                if (serverWorlds != null && serverWorlds.length > 0 && serverWorlds[0] != null)
                {
                    setWorldTime.invoke(serverWorlds[0], Long.valueOf(6000L));
                }
            }
            hideGui.setBoolean(gameSettings.get(minecraft), !SHOW_HUD);
            // difficulty is EnumDifficulty in 1.7.10; PEACEFUL is the first constant.
            difficulty.set(gameSettings.get(minecraft), difficulty.getType().getEnumConstants()[0]);
            // The focusless test window would auto-pause into GuiIngameMenu, which is
            // what earlier captures actually photographed; keep every screen closed.
            displayGuiScreen.invoke(minecraft, new Object[] {null});

            // Anchor at the WORLD's fixed spawn point: player respawn positions scatter
            // by several blocks between runs (three runs anchored at three different
            // columns when the player's own position was used), but getSpawnPoint is a
            // world constant and its chunks are always loaded.
            if (!anchored)
            {
                Object spawn = getSpawnPoint.invoke(world);
                Class<?> coords = spawn.getClass();
                java.lang.reflect.Field cx = coords.getDeclaredField("a");
                java.lang.reflect.Field cz = coords.getDeclaredField("c");
                cx.setAccessible(true);
                cz.setAccessible(true);
                anchorX = cx.getInt(spawn) + 0.5D;
                anchorZ = cz.getInt(spawn) + 0.5D;
                int height = ((Integer)getHeightValue.invoke(world, Integer.valueOf(cx.getInt(spawn)), Integer.valueOf(cz.getInt(spawn)))).intValue();

                if (height <= 0)
                {
                    return;
                }

                groundY = height + 1.62D;
                anchored = true;
                LogWrapper.info("[Vertex] Frame capture anchored at " + (int)anchorX + "," + height + "," + (int)anchorZ);
            }

            if (MOTION)
            {
                // Motion mode: fly smoothly along +Z at fixed altitude, capturing a burst
                // of sequential frames. Temporal artifacts (section flicker, pop-in) show
                // as consecutive-frame diff spikes that steady parallax never produces.
                if (motionZ == 0.0D)
                {
                    motionZ = anchorZ;
                }

                if (now - worldSeenMs >= SETTLE_MS)
                {
                    motionZ += MOTION_SPEED;
                }

                setPosition.invoke(player, Double.valueOf(anchorX), Double.valueOf(groundY + 20.0D), Double.valueOf(motionZ));
                rotationYaw.setFloat(player, 0.0F);
                rotationPitch.setFloat(player, 25.0F);
            }
            else
            {
                setPosition.invoke(player, Double.valueOf(anchorX), Double.valueOf(groundY), Double.valueOf(anchorZ));
                rotationYaw.setFloat(player, YAWS[angleIndex]);
                rotationPitch.setFloat(player, 10.0F);
            }

            if (now - worldSeenMs < SETTLE_MS)
            {
                return;
            }

            // Capture only a FULLY built world: chunk streaming order differs between
            // runs, so photographing mid-stream compares different completion states
            // (angle 0 matched at 5.8% while later angles diverged 70% - the far field
            // was still popping in). Require an empty build queue for 300 consecutive
            // frames before any capture.
            // Motion mode captures THROUGH chunk streaming - a moving player rebuilds
            // continuously, so a drain gate would never open, and streaming behavior is
            // exactly what the temporal comparison exists to observe.
            if (!MOTION)
            {
                int pending = VertexHooks.pendingUpdates(renderGlobalField.get(minecraft));

                if (pending != 0)
                {
                    drainedFrames = 0;
                    return;
                }

                if (++drainedFrames < 300)
                {
                    return;
                }
            }

            if (++settleFrames < FRAMES_PER_ANGLE)
            {
                return;
            }

            if (angleIndex == 0)
            {
                auditGrid(minecraft);
            }

            capture(angleIndex);
            settleFrames = 0;

            if (++angleIndex >= YAWS.length)
            {
                done = true;
                LogWrapper.info("[Vertex] Frame capture complete (" + YAWS.length + " angles)");
                restorePinnedSettings(minecraft);
            }
        }
        catch (Throwable e)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Frame capture disabled after failure");
            e.printStackTrace();
            restorePinnedSettings(minecraft);
        }
    }

    /**
     * The per-tick pins overwrite live gameSettings, and the game persists gameSettings
     * to options.txt on shutdown - so pinned values outlived the run and followed any
     * copy of the game dir into normal play (#115: a play dir seeded from a harness dir
     * kept Peaceful difficulty, presenting as a hunger/regeneration bug). Restore the
     * armed-time snapshot the moment the capture reaches any terminal state; after this
     * the pins stop (active() is false), so the restored values persist.
     */
    private static void restorePinnedSettings(Object minecraft)
    {
        if (!settingsSnapshotTaken)
        {
            return;
        }

        try
        {
            Object settings = gameSettings.get(minecraft);
            hideGui.setBoolean(settings, savedHideGui);
            difficulty.set(settings, savedDifficulty);
            LogWrapper.info("[Vertex] Frame capture restored pinned settings (difficulty, hideGui)");
        }
        catch (Exception e)
        {
            LogWrapper.warning("[Vertex] Frame capture could not restore pinned settings: " + e);
        }
    }

    /**
     * Per-section build audit: position, bytesDrawn and skip flags for every renderer in
     * the grid, written beside the shots. Diffing an off-run table against an on-run
     * table separates quads lost at capture (byte deficit) from quads misdisplayed at
     * replay (equal bytes, wrong picture).
     */
    private static void auditGrid(Object minecraft) throws Exception
    {
        Object rg = renderGlobalField.get(minecraft);
        Field grid = rg.getClass().getDeclaredField(vertex.Mappings.RG_WORLD_RENDERERS);
        grid.setAccessible(true);
        Object[] renderers = (Object[])grid.get(rg);

        if (renderers == null || renderers.length == 0)
        {
            return;
        }

        Class<?> wr = renderers[0].getClass();
        Field px = wr.getDeclaredField(vertex.Mappings.WR_POS_X);
        Field py = wr.getDeclaredField(vertex.Mappings.WR_POS_Y);
        Field pz = wr.getDeclaredField(vertex.Mappings.WR_POS_Z);
        Field bytes = wr.getDeclaredField(vertex.Mappings.WR_BYTES_DRAWN);
        Field skip = wr.getDeclaredField("m");
        px.setAccessible(true);
        py.setAccessible(true);
        pz.setAccessible(true);
        bytes.setAccessible(true);
        skip.setAccessible(true);
        StringBuilder out = new StringBuilder("x\ty\tz\tbytes\tskip0\tskip1\n");

        for (Object renderer : renderers)
        {
            boolean[] skips = (boolean[])skip.get(renderer);
            out.append(px.getInt(renderer)).append('\t').append(py.getInt(renderer)).append('\t').append(pz.getInt(renderer))
               .append('\t').append(bytes.getInt(renderer)).append('\t').append(skips[0]).append('\t').append(skips[1]).append('\n');
        }

        File dir = new File(SHOT_DIR);
        dir.mkdirs();
        FileOutputStream auditOut = new FileOutputStream(new File(dir, "build-audit.tsv"));
        auditOut.write(out.toString().getBytes("UTF-8"));
        auditOut.close();
        LogWrapper.info("[Vertex] Build audit written (" + renderers.length + " sections)");
    }

    private static byte[] captureRaw()
    {
        int width = Display.getWidth();
        int height = Display.getHeight();
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 3).order(ByteOrder.nativeOrder());
        GL11.glReadBuffer(GL11.GL_FRONT);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buffer);
        byte[] row = new byte[width * 3];
        byte[] raw = new byte[width * height * 3];

        for (int y = 0; y < height; ++y)
        {
            buffer.position((height - 1 - y) * width * 3);
            buffer.get(row);
            System.arraycopy(row, 0, raw, y * width * 3, row.length);
        }

        return raw;
    }

    private static void writeShot(int index, byte[] raw) throws Exception
    {
        int width = Display.getWidth();
        int height = Display.getHeight();
        File dir = new File(SHOT_DIR);
        dir.mkdirs();
        FileOutputStream shotOut = new FileOutputStream(new File(dir, "shot-" + index + ".rgb"));
        shotOut.write(raw);
        shotOut.close();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; ++y)
        {
            for (int x = 0; x < width; ++x)
            {
                int base = (y * width + x) * 3;
                image.setRGB(x, y, (raw[base] & 0xFF) << 16 | (raw[base + 1] & 0xFF) << 8 | (raw[base + 2] & 0xFF));
            }
        }

        ImageIO.write(image, "png", new File(dir, "shot-" + index + ".png"));
    }

    private static void capture(int index) throws Exception
    {
        byte[] raw = captureRaw();
        File dir = new File(SHOT_DIR);
        dir.mkdirs();
        int width = Display.getWidth();
        int height = Display.getHeight();

        writeShot(index, raw);
        LogWrapper.info("[Vertex] Captured shot-" + index + " (" + width + "x" + height + ")");
    }

    private static void initialize(Object minecraft, Object world, Object player) throws Exception
    {
        Class<?> entity = player.getClass();

        while (entity.getSuperclass() != Object.class)
        {
            entity = entity.getSuperclass();
        }

        setPosition = declared(entity, Mappings.ENTITY_SET_POSITION, double.class, double.class, double.class);
        rotationYaw = entity.getDeclaredField(Mappings.ENTITY_ROTATION_YAW);
        rotationYaw.setAccessible(true);
        rotationPitch = entity.getDeclaredField(Mappings.ENTITY_ROTATION_PITCH);
        rotationPitch.setAccessible(true);
        Class<?> worldBase = world.getClass();

        while (worldBase.getSuperclass() != Object.class)
        {
            worldBase = worldBase.getSuperclass();
        }

        setWorldTime = declared(worldBase, Mappings.WORLD_SET_TIME, long.class);
        getHeightValue = declared(worldBase, "f", int.class, int.class);
        getSpawnPoint = declared(worldBase, "K");
        getIntegratedServer = minecraft.getClass().getMethod("H");
        Class<?> serverClass = getIntegratedServer.getReturnType();

        while (serverClass.getSuperclass() != Object.class && !hasField(serverClass, "c"))
        {
            serverClass = serverClass.getSuperclass();
        }

        worldServers = serverClass.getDeclaredField("c");
        worldServers.setAccessible(true);

        for (Method candidate : minecraft.getClass().getMethods())
        {
            if (candidate.getName().equals("a") && candidate.getParameterTypes().length == 1
                && candidate.getParameterTypes()[0].getName().equals("bdw"))
            {
                displayGuiScreen = candidate;
                break;
            }
        }

        gameSettings = minecraft.getClass().getDeclaredField(Mappings.MC_GAME_SETTINGS);
        gameSettings.setAccessible(true);
        hideGui = gameSettings.get(minecraft).getClass().getDeclaredField(Mappings.GS_HIDE_GUI);
        hideGui.setAccessible(true);
        difficulty = gameSettings.get(minecraft).getClass().getDeclaredField("au");
        difficulty.setAccessible(true);
        // Snapshot before the first pin so every terminal state can restore what the
        // player actually had (#115).
        savedHideGui = hideGui.getBoolean(gameSettings.get(minecraft));
        savedDifficulty = difficulty.get(gameSettings.get(minecraft));
        settingsSnapshotTaken = true;
        posXField = entity.getDeclaredField(Mappings.ENTITY_POS_X);
        posXField.setAccessible(true);
        posZField = entity.getDeclaredField(Mappings.ENTITY_POS_Z);
        posZField.setAccessible(true);
        renderGlobalField = minecraft.getClass().getDeclaredField(Mappings.MC_RENDER_GLOBAL);
        renderGlobalField.setAccessible(true);
        renderDistanceField = gameSettings.get(minecraft).getClass().getDeclaredField(Mappings.GS_RENDER_DISTANCE);
        renderDistanceField.setAccessible(true);
        initialized = true;
    }

    private static double entityPosX(Object player) throws Exception
    {
        return posXField.getDouble(player);
    }

    private static double entityPosZ(Object player) throws Exception
    {
        return posZField.getDouble(player);
    }

    private static boolean hasField(Class<?> owner, String name)
    {
        try
        {
            owner.getDeclaredField(name);
            return true;
        }
        catch (NoSuchFieldException absent)
        {
            return false;
        }
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

    private VertexFrameCapture()
    {
    }
}
