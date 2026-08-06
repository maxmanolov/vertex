package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Owner of the shared Tessellator reference once the redirect transformer has rewritten
 * every read of Tessellator.instance into {@link #get}. Phase 1 (this class as-is) is an
 * identity redirect: bind() captures the vanilla instance from Tessellator's own static
 * initializer and get() hands it back, so behavior is exactly vanilla while proving the
 * rewrite is total. Phase 2 (multi-core chunk building) turns the lookup per-thread so
 * chunk-build workers tessellate concurrently into their own instances.
 *
 * Typed as Object throughout: the obfuscated Tessellator class is invisible at compile
 * time, and the call sites cast back via an injected CHECKCAST.
 */
public final class VertexTessellator
{
    private static volatile Object mainInstance;

    /**
     * Per-thread resolution (multi-core phase 2 groundwork): chunk-build workers bind their
     * own instance once; every other thread - including the client thread - falls through
     * to the main instance captured from Tessellator.<clinit>. Until workers exist nothing
     * ever binds an alternate instance, so behavior is exactly phase 1's.
     */
    // Holds ONLY explicit worker bindings. It must never cache mainInstance: a cached
    // copy would survive a rebind and hand out a stale instance (and initialValue-style
    // caching did exactly that under test).
    private static final ThreadLocal<Object> threadInstance = new ThreadLocal<Object>();

    /** Called from the tail of Tessellator.<clinit> with the freshly constructed instance. */
    public static void bind(Object tessellator)
    {
        mainInstance = tessellator;
        LogWrapper.info("[Vertex] Tessellator redirect active (" + vertex.transform.TessellatorRedirectPatch.rewrittenSites()
            + " call sites rewritten so far)");
    }

    public static void bindThreadInstance(Object tessellator)
    {
        threadInstance.set(tessellator);
    }

    public static Object get()
    {
        Object bound = threadInstance.get();
        return bound != null ? bound : mainInstance;
    }

    /**
     * A render failure can leave the main Tessellator in drawing state. Vanilla then
     * rejects the first draw after a world change with "Already tesselating!". A world
     * change is a client-thread boundary, so no valid draw can span it. Reset abandoned
     * buffer state and clear the drawing flag before the next world uses the instance.
     */
    public static void sanitizeOnWorldChange(Object minecraft)
    {
        Object tessellator = mainInstance;

        if (tessellator == null)
        {
            return;
        }

        try
        {
            Field isDrawing = tessellator.getClass().getDeclaredField(Mappings.TESS_IS_DRAWING);
            isDrawing.setAccessible(true);

            if (!isDrawing.getBoolean(tessellator))
            {
                return;
            }

            try
            {
                Method reset = tessellator.getClass().getDeclaredMethod(Mappings.TESS_RESET);
                reset.setAccessible(true);
                reset.invoke(tessellator);
            }
            finally
            {
                // startDrawing resets the buffer again. Clear this even if the direct
                // reset failed, so an abandoned draw cannot poison the next world.
                isDrawing.setBoolean(tessellator, false);
            }

            LogWrapper.warning("[Vertex] Recovered abandoned tessellation at world change");
        }
        catch (Exception e)
        {
            LogWrapper.warning("[Vertex] Could not recover tessellation at world change: " + e);
        }
    }

    private VertexTessellator()
    {
    }
}
