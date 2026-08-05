package vertex.hooks;

import net.minecraft.launchwrapper.LogWrapper;

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

    /** Called from the tail of Tessellator.<clinit> with the freshly constructed instance. */
    public static void bind(Object tessellator)
    {
        mainInstance = tessellator;
        LogWrapper.info("[Vertex] Tessellator redirect active (" + vertex.transform.TessellatorRedirectPatch.rewrittenSites()
            + " call sites rewritten so far)");
    }

    public static Object get()
    {
        return mainInstance;
    }

    private VertexTessellator()
    {
    }
}
