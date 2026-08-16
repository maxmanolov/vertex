package vertex.hooks;

import java.lang.reflect.Method;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Smooth Biomes fast path: vanilla 1.7.10 always averages grass/foliage colors over a
 * 3x3 biome neighborhood per block face - nine biome lookups per colored block per
 * rebuild. With smoothBiomes=false, the blenders return the center biome's color alone,
 * trading the blended transition band at biome borders for one lookup.
 *
 * The center sample runs on the tessellation threads; handles resolve once from the
 * first live world/biome pair, and any failure permanently reverts to the vanilla
 * blending path (the guard goes false, the blend loop resumes next call).
 */
public final class VertexSmoothBiomes
{
    static final int KIND_GRASS = 0;
    static final int KIND_FOLIAGE = 1;

    private static volatile boolean ready = false;
    private static boolean disabled = false;

    private static Method biomeByCoords;
    private static Method grassColor;
    private static Method foliageColor;

    public static long centerSamples = 0L;

    /** Guard: true routes the blender through the center sample. */
    public static boolean fastPath()
    {
        return !disabled && !VertexConfig.enabled("smoothBiomes");
    }

    /** Replacement body: the center biome's color for the given sample kind. */
    public static int centerSample(Object world, int x, int y, int z, int kind)
    {
        try
        {
            if (!ready)
            {
                resolve(world, x, z);
            }

            Object biome = biomeByCoords.invoke(world, Integer.valueOf(x), Integer.valueOf(z));
            Method sample = kind == KIND_FOLIAGE ? foliageColor : grassColor;
            ++centerSamples;
            return ((Integer)sample.invoke(biome, Integer.valueOf(x), Integer.valueOf(y),
                Integer.valueOf(z))).intValue();
        }
        catch (Throwable t)
        {
            disable(t);
            // One block renders uncolored for one build; the guard is off from the
            // next call and the vanilla blend recomputes it on any later rebuild.
            return 0xFFFFFF;
        }
    }

    private static synchronized void resolve(Object world, int x, int z) throws Exception
    {
        if (ready || disabled)
        {
            return;
        }

        // ahl is an interface; the method resolves from it regardless of the concrete
        // world/chunk-cache class the tessellation threads hand us.
        for (Class<?> iface : interfacesOf(world.getClass()))
        {
            try
            {
                biomeByCoords = iface.getMethod(Mappings.BIOME_BY_COORDS, int.class, int.class);
                break;
            }
            catch (NoSuchMethodException ignored)
            {
                // keep scanning
            }
        }

        if (biomeByCoords == null)
        {
            biomeByCoords = world.getClass().getMethod(Mappings.BIOME_BY_COORDS, int.class, int.class);
        }

        biomeByCoords.setAccessible(true);
        Object biome = biomeByCoords.invoke(world, Integer.valueOf(x), Integer.valueOf(z));
        // Resolve on the biome ROOT class: each call may hand a different concrete
        // biome, and virtual dispatch must keep honoring overrides (the swamp's
        // special colors - and their swampColors guard - included).
        Class<?> biomeRoot = biome.getClass();

        while (biomeRoot.getSuperclass() != null && biomeRoot.getSuperclass() != Object.class)
        {
            biomeRoot = biomeRoot.getSuperclass();
        }

        grassColor = biomeRoot.getMethod(Mappings.BIOME_GRASS_COLOR,
            int.class, int.class, int.class);
        grassColor.setAccessible(true);
        foliageColor = biomeRoot.getMethod(Mappings.BIOME_FOLIAGE_COLOR,
            int.class, int.class, int.class);
        foliageColor.setAccessible(true);
        ready = true;
        LogWrapper.info("[Vertex] Smooth biomes fast path armed");
    }

    private static java.util.List<Class<?>> interfacesOf(Class<?> start)
    {
        java.util.List<Class<?>> out = new java.util.ArrayList<Class<?>>();

        for (Class<?> cls = start; cls != null; cls = cls.getSuperclass())
        {
            for (Class<?> iface : cls.getInterfaces())
            {
                out.add(iface);
            }
        }

        return out;
    }

    private static void disable(Throwable t)
    {
        if (!disabled)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Smooth biomes fast path disabled after failure");
            t.printStackTrace();
        }
    }

    private VertexSmoothBiomes()
    {
    }
}
