package vertex.api;

/**
 * Implemented onto the vanilla WorldRenderer class by the Vertex transformer. Gives hook
 * code typed access to the injected immediate-update state and to the renderer's rebuild
 * entry point without any per-call reflection. Parameters are typed as Object because the
 * obfuscated Minecraft types are not visible at compile time; the injected bridge methods
 * perform the casts.
 */
public interface ImmediateMarker
{
    void vertex$markImmediate();

    boolean vertex$needsImmediate();

    void vertex$clearImmediate();

    /** Mirror of WorldRenderer.needsUpdate. */
    boolean vertex$isDirty();

    /** Invokes WorldRenderer.updateRenderer(EntityLivingBase); the argument must be the view entity. */
    void vertex$rebuild(Object viewEntity);

    /** Bridge to the private setupGLTranslation, used by the multi-core replay. */
    void vertex$setupTranslation();
}
