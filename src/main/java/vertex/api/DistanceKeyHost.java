package vertex.api;

/**
 * Injected into the vanilla WorldRenderer class. Renderer-array sorts derive a camera
 * distance once per section, store it here, and compare the primitive keys instead of
 * repeating the same coordinate arithmetic from the comparison callback.
 */
public interface DistanceKeyHost
{
    double vertex$sortKey();

    void vertex$setSortKey(double key);

    int vertex$centerX();

    int vertex$centerY();

    int vertex$centerZ();
}
