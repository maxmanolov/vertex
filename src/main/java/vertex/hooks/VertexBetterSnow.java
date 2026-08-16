package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Better snow: fences, walls, plants and other non-cube blocks whose neighbor carries a
 * snow layer get a one-layer snow box tessellated underneath them, so snowy ground reads
 * as continuous instead of showing bare dirt squares under every fence post.
 *
 * Mechanism: a head hook on RenderBlocks' per-block dispatch. When the setting is on,
 * the block's render type is in the snowy set, and a horizontal neighbor at the same
 * height is a snow layer, the hook renders Blocks.snow_layer at the host position first
 * (explicit one-layer bounds under the bounds lock - snow_layer's own bounds read the
 * host block's metadata and would leak its value as a snow height), then always lets
 * the vanilla body draw the block itself.
 *
 * Runs on the tessellation threads: resolution is synchronized and one-time, per-call
 * state lives on the caller's own RenderBlocks instance, and any failure disables the
 * feature permanently in the vanilla direction.
 */
public final class VertexBetterSnow
{
    private static volatile boolean ready = false;
    private static boolean disabled = false;

    private static Field blockAccess;
    private static Method getBlock;
    private static Method overrideBounds;
    private static Method renderStandard;
    private static Field boundsLock;
    private static Method renderType;
    private static Object snowLayer;

    public static long prepended = 0L;

    /** Head guard on renderBlockByRenderType; always returns false (never skips). */
    public static boolean prepend(Object renderBlocks, Object block, int x, int y, int z)
    {
        if (disabled || block == null || !VertexConfig.enabled("betterSnow"))
        {
            return false;
        }

        try
        {
            if (!ready)
            {
                resolve(renderBlocks, block);
            }

            if (block == snowLayer)
            {
                return false;
            }

            int type = ((Integer)renderType.invoke(block)).intValue();

            if (!snowyRenderType(type))
            {
                return false;
            }

            Object world = blockAccess.get(renderBlocks);

            if (world == null || !neighborHasSnowLayer(world, x, y, z))
            {
                return false;
            }

            overrideBounds.invoke(renderBlocks, Double.valueOf(0.0D), Double.valueOf(0.0D),
                Double.valueOf(0.0D), Double.valueOf(1.0D), Double.valueOf(0.125D), Double.valueOf(1.0D));

            try
            {
                renderStandard.invoke(renderBlocks, snowLayer,
                    Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
            }
            finally
            {
                boundsLock.setBoolean(renderBlocks, false);
            }

            ++prepended;
        }
        catch (Throwable t)
        {
            disable(t);
        }

        return false;
    }

    /**
     * Render types that sit on the ground without covering it: crossed plants (1),
     * torches (2), ladders (8), fences (11), panes (18), walls (32), double plants (40).
     */
    static boolean snowyRenderType(int type)
    {
        return type == 1 || type == 2 || type == 8 || type == 11 || type == 18
            || type == 32 || type == 40;
    }

    private static boolean neighborHasSnowLayer(Object world, int x, int y, int z) throws Exception
    {
        return getBlock.invoke(world, Integer.valueOf(x - 1), Integer.valueOf(y), Integer.valueOf(z)) == snowLayer
            || getBlock.invoke(world, Integer.valueOf(x + 1), Integer.valueOf(y), Integer.valueOf(z)) == snowLayer
            || getBlock.invoke(world, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z - 1)) == snowLayer
            || getBlock.invoke(world, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z + 1)) == snowLayer;
    }

    private static synchronized void resolve(Object renderBlocks, Object block) throws Exception
    {
        if (ready || disabled)
        {
            return;
        }

        Class<?> rbClass = renderBlocks.getClass();
        blockAccess = rbClass.getDeclaredField(Mappings.RB_BLOCK_ACCESS);
        blockAccess.setAccessible(true);
        getBlock = blockAccess.getType().getMethod(Mappings.ACCESS_GET_BLOCK,
            int.class, int.class, int.class);
        getBlock.setAccessible(true);
        overrideBounds = rbClass.getMethod(Mappings.RB_OVERRIDE_BOUNDS,
            double.class, double.class, double.class, double.class, double.class, double.class);
        overrideBounds.setAccessible(true);
        boundsLock = rbClass.getDeclaredField(Mappings.RB_BOUNDS_LOCK);
        boundsLock.setAccessible(true);

        // Block's root class declares the render type; the concrete block is a subclass.
        Class<?> blockRoot = block.getClass();

        while (blockRoot.getSuperclass() != null && blockRoot.getSuperclass() != Object.class)
        {
            blockRoot = blockRoot.getSuperclass();
        }

        renderType = blockRoot.getMethod(Mappings.BLOCK_RENDER_TYPE);
        renderType.setAccessible(true);
        renderStandard = rbClass.getMethod(Mappings.RB_RENDER_STANDARD,
            blockRoot, int.class, int.class, int.class);
        renderStandard.setAccessible(true);

        // Same-classloader lookup as the live block; never Class.forName across the
        // LaunchWrapper split.
        Class<?> blocks = block.getClass().getClassLoader().loadClass(Mappings.BLOCKS_REGISTRY);
        Field layer = blocks.getField(Mappings.BLOCKS_SNOW_LAYER);
        snowLayer = layer.get(null);
        ready = true;
        LogWrapper.info("[Vertex] Better snow armed");
    }

    private static void disable(Throwable t)
    {
        if (!disabled)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Better snow disabled after failure");
            t.printStackTrace();
        }
    }

    private VertexBetterSnow()
    {
    }
}
