package vertex.render;

import java.lang.reflect.Field;
import vertex.Mappings;

/**
 * Backend-neutral CPU geometry for one render pass of one chunk section: the exact
 * interleaved vertex stream a 1.7.10 Tessellator would hand to glDrawArrays, detached
 * from any GL object. Producers (chunk-build workers or the client-thread capture)
 * extract it from a filled Tessellator; a {@link RenderBackend} turns it into a GPU
 * representation on the client thread.
 *
 * Layout per vertex is the vanilla 32-byte stride, verified against bmh.a()I bytecode:
 * position 3f @0, texture UV 2f @12, color 4ub @20, normal 3b @24, lightmap UV 2s @28.
 * Vertices are section-local (the build set the tessellator translation to -origin), so
 * world position = vertex + origin and every backend chooses its own transform strategy:
 * matrix translate per section now, baked clip coordinates in the shared-arena stage.
 *
 * Instances are immutable after construction and safe to hand across threads; the int[]
 * is an exact-size copy owned by this object.
 */
public final class MeshData
{
    /** Ints per vertex in the vanilla interleaved format. */
    public static final int INTS_PER_VERTEX = 8;
    /** Bytes per vertex (the GL stride). */
    public static final int STRIDE = 32;

    public final int[] data;
    public final int vertexCount;
    public final int drawMode;
    public final boolean hasTexture;
    public final boolean hasBrightness;
    public final boolean hasColor;
    public final boolean hasNormals;

    public MeshData(int[] data, int vertexCount, int drawMode,
        boolean hasTexture, boolean hasBrightness, boolean hasColor, boolean hasNormals)
    {
        this.data = data;
        this.vertexCount = vertexCount;
        this.drawMode = drawMode;
        this.hasTexture = hasTexture;
        this.hasBrightness = hasBrightness;
        this.hasColor = hasColor;
        this.hasNormals = hasNormals;
    }

    public boolean isEmpty()
    {
        return this.vertexCount == 0;
    }

    /** Payload size in bytes as uploaded (used ints only). */
    public int byteSize()
    {
        return this.vertexCount * STRIDE;
    }

    /**
     * Reflective extractor, usable from any thread against that thread's own Tessellator.
     * Copies only the used prefix of the raw buffer, so the (potentially 8 MB) tessellator
     * can be reset and recycled immediately after this returns. Handles are cached per
     * class, not per instance; the game only ever loads one Tessellator class.
     */
    public static final class Extractor
    {
        private Field rawBuffer;
        private Field rawBufferIndex;
        private Field vertexCount;
        private Field drawMode;
        private Field hasColor;
        private Field hasTexture;
        private Field hasBrightness;
        private Field hasNormals;
        private volatile boolean resolved = false;

        public MeshData extract(Object tessellator) throws Exception
        {
            if (!this.resolved)
            {
                resolve(tessellator.getClass());
            }

            int usedInts = this.rawBufferIndex.getInt(tessellator);
            int[] copy = new int[usedInts];

            if (usedInts > 0)
            {
                System.arraycopy((int[])this.rawBuffer.get(tessellator), 0, copy, 0, usedInts);
            }

            return new MeshData(copy,
                this.vertexCount.getInt(tessellator),
                this.drawMode.getInt(tessellator),
                this.hasTexture.getBoolean(tessellator),
                this.hasBrightness.getBoolean(tessellator),
                this.hasColor.getBoolean(tessellator),
                this.hasNormals.getBoolean(tessellator));
        }

        private synchronized void resolve(Class<?> tess) throws NoSuchFieldException
        {
            if (this.resolved)
            {
                return;
            }

            this.rawBuffer = accessible(tess, Mappings.TESS_RAW_BUFFER);
            this.rawBufferIndex = accessible(tess, Mappings.TESS_RAW_BUFFER_INDEX);
            this.vertexCount = accessible(tess, Mappings.TESS_VERTEX_COUNT);
            this.drawMode = accessible(tess, Mappings.TESS_DRAW_MODE);
            this.hasColor = accessible(tess, Mappings.TESS_HAS_COLOR);
            this.hasTexture = accessible(tess, Mappings.TESS_HAS_TEXTURE);
            this.hasBrightness = accessible(tess, Mappings.TESS_HAS_BRIGHTNESS);
            this.hasNormals = accessible(tess, Mappings.TESS_HAS_NORMALS);
            this.resolved = true;
        }

        private static Field accessible(Class<?> owner, String name) throws NoSuchFieldException
        {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }
}
