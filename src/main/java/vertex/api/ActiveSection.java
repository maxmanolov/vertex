package vertex.api;

/** Game-type-free view of the fields needed by the compact render-section registry. */
public interface ActiveSection
{
    boolean vertex$hasMesh();

    int vertex$centerX();

    int vertex$centerY();

    int vertex$centerZ();
}
