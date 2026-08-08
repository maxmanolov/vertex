package vertex.api;

/**
 * Injected into WorldRenderer alongside {@link ImmediateMarker}: one opaque slot where
 * the live render backend parks its per-section GPU state (nothing for display lists,
 * buffer ids for the VBO backend, an arena range later). Resolved through the app class
 * loader from both sides of the loader split, exactly like ImmediateMarker, so backends
 * reach their state without reflection on the hot path.
 */
public interface MeshHost
{
    Object vertex$mesh();

    void vertex$setMesh(Object state);
}
