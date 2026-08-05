package vertex.hooks;

/**
 * TailCallPatch emits a no-arg static call, but the sky renderer needs the RenderGlobal
 * instance to reach the world. The bridge holds the instance published by the existing
 * per-frame hooks, keeping the patch primitive unchanged.
 */
public final class VertexSkyBridge
{
    private static volatile Object renderGlobal;

    public static void publish(Object instance)
    {
        renderGlobal = instance;
    }

    public static void afterSky()
    {
        Object instance = renderGlobal;

        if (instance != null)
        {
            VertexSky.renderLayers(instance);
        }
    }

    private VertexSkyBridge()
    {
    }
}
