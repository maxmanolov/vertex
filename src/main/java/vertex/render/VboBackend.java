package vertex.render;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;
import vertex.api.MeshHost;

/**
 * Per-section VBO backend (`renderer=vbo`, stage 2 of docs/RENDERER.md): each
 * (section, pass) owns one buffer object, uploads replace its content with
 * glBufferData, and the backend owns submission - inside vanilla's lightmap bracket it
 * walks the pass's visible sections in vanilla's order and draws each with
 * bind + pointers + the vanilla section transform + glDrawArrays.
 *
 * Buffers are slot-bound: allocated lazily per grid slot, content replaced on rebuild,
 * released only at reset (world change, render-distance change, disable) - steady state
 * allocates nothing. Every allocated id is also tracked here, independent of the
 * renderer objects, so reset can release GPU memory even though the grid's renderer
 * objects (and their MeshHost slots) are replaced wholesale by loadRenderers. Slots
 * from before a reset identify themselves by generation and are simply skipped.
 *
 * Client-state enables are diffed against the previous mesh instead of toggled per
 * section: terrain meshes share one format in practice, so a pass costs one state
 * setup, not thousands of redundant enables (the GL-redundancy diagnostic counts those).
 */
public final class VboBackend implements RenderBackend
{
    private static final float SECTION_SCALE = 1.000001F;

    /** Per-section state parked in the injected MeshHost slot. */
    private static final class Slots
    {
        final int generation;
        final PassMesh[] passes = {new PassMesh(), new PassMesh()};

        Slots(int generation)
        {
            this.generation = generation;
        }
    }

    private static final class PassMesh
    {
        int vbo = -1;
        int vertexCount;
        int drawMode;
        int bytes;
        boolean hasTexture;
        boolean hasBrightness;
        boolean hasColor;
        boolean hasNormals;
        int originX;
        int originY;
        int originZ;
    }

    private int generation = 0;
    private final List<Integer> allBuffers = new ArrayList<Integer>();
    private long bufferBytes = 0L;

    private long uploads = 0L;
    private long uploadedBytes = 0L;
    private long uploadNanos = 0L;
    private long sectionsDrawn = 0L;
    private long drawCalls = 0L;
    private long drawNanos = 0L;

    public VboBackend()
    {
        if (!GLContext.getCapabilities().OpenGL15)
        {
            throw new IllegalStateException("OpenGL 1.5 buffer objects are unavailable");
        }
    }

    @Override
    public String name()
    {
        return "vbo";
    }

    @Override
    public void upload(Object renderer, int pass, MeshData mesh, int originX, int originY, int originZ, int glListBase)
    {
        long start = System.nanoTime();
        MeshHost host = (MeshHost)renderer;
        Object parked = host.vertex$mesh();
        Slots slots = parked instanceof Slots ? (Slots)parked : null;

        if (slots == null || slots.generation != this.generation)
        {
            slots = new Slots(this.generation);
            host.vertex$setMesh(slots);
        }

        PassMesh target = slots.passes[pass];

        if (target.vbo == -1)
        {
            target.vbo = GL15.glGenBuffers();
            this.allBuffers.add(Integer.valueOf(target.vbo));
        }

        try
        {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, target.vbo);

            if (mesh.isEmpty())
            {
                // An empty rebuild must still overwrite: release the storage, keep the id.
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 0L, GL15.GL_STATIC_DRAW);
                this.bufferBytes -= target.bytes;
                target.bytes = 0;
                target.vertexCount = 0;
            }
            else
            {
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, Staging.load(mesh), GL15.GL_STATIC_DRAW);
                this.bufferBytes += mesh.byteSize() - target.bytes;
                target.bytes = mesh.byteSize();
                target.vertexCount = mesh.vertexCount;
                target.drawMode = mesh.drawMode;
                target.hasTexture = mesh.hasTexture;
                target.hasBrightness = mesh.hasBrightness;
                target.hasColor = mesh.hasColor;
                target.hasNormals = mesh.hasNormals;
                target.originX = originX;
                target.originY = originY;
                target.originZ = originZ;
            }
        }
        finally
        {
            // A leaked binding would make every later client-array draw (chat, GUI,
            // vanilla fallback) read this buffer instead of its own memory.
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
        ++this.uploads;
        this.uploadedBytes += mesh.byteSize();
        this.uploadNanos += System.nanoTime() - start;
    }

    @Override
    public boolean ownsSubmission()
    {
        return true;
    }

    @Override
    public void drawVisible(List<?> sections, int pass, double camX, double camY, double camZ)
    {
        long start = System.nanoTime();
        boolean vertexOn = false;
        boolean textureOn = false;
        boolean brightnessOn = false;
        boolean colorOn = false;
        boolean normalsOn = false;
        int drawn = 0;

        try
        {
            for (int i = 0; i < sections.size(); ++i)
            {
                Object renderer = sections.get(i);

                if (!(renderer instanceof MeshHost))
                {
                    continue;
                }

                Object parked = ((MeshHost)renderer).vertex$mesh();

                if (!(parked instanceof Slots))
                {
                    continue;
                }

                Slots slots = (Slots)parked;

                if (slots.generation != this.generation)
                {
                    continue;
                }

                PassMesh mesh = slots.passes[pass];

                if (mesh.vertexCount == 0)
                {
                    continue;
                }

                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.vbo);
                // Pointers capture the buffer binding, so they re-issue per section;
                // the enables are global state and only toggle when the format changes.
                GL11.glVertexPointer(3, GL11.GL_FLOAT, MeshData.STRIDE, 0L);

                if (!vertexOn)
                {
                    GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                    vertexOn = true;
                }

                if (mesh.hasTexture)
                {
                    GL11.glTexCoordPointer(2, GL11.GL_FLOAT, MeshData.STRIDE, 12L);
                }

                if (mesh.hasTexture != textureOn)
                {
                    setClientState(GL11.GL_TEXTURE_COORD_ARRAY, mesh.hasTexture);
                    textureOn = mesh.hasTexture;
                }

                if (mesh.hasBrightness)
                {
                    Staging.clientActiveTexture(Staging.lightmapUnit());
                    GL11.glTexCoordPointer(2, GL11.GL_SHORT, MeshData.STRIDE, 28L);

                    if (!brightnessOn)
                    {
                        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                        brightnessOn = true;
                    }

                    Staging.clientActiveTexture(Staging.defaultUnit());
                }
                else if (brightnessOn)
                {
                    Staging.clientActiveTexture(Staging.lightmapUnit());
                    GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    Staging.clientActiveTexture(Staging.defaultUnit());
                    brightnessOn = false;
                }

                if (mesh.hasColor)
                {
                    GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, MeshData.STRIDE, 20L);
                }

                if (mesh.hasColor != colorOn)
                {
                    setClientState(GL11.GL_COLOR_ARRAY, mesh.hasColor);
                    colorOn = mesh.hasColor;
                }

                if (mesh.hasNormals)
                {
                    GL11.glNormalPointer(GL11.GL_BYTE, MeshData.STRIDE, 24L);
                }

                if (mesh.hasNormals != normalsOn)
                {
                    setClientState(GL11.GL_NORMAL_ARRAY, mesh.hasNormals);
                    normalsOn = mesh.hasNormals;
                }

                GL11.glPushMatrix();
                // Double subtraction before the float cast, like RenderList's base-minus-
                // camera translate; clip+minus fold into the origin so precision matches.
                GL11.glTranslatef(
                    (float)((double)mesh.originX - camX),
                    (float)((double)mesh.originY - camY),
                    (float)((double)mesh.originZ - camZ));
                GL11.glTranslatef(-8.0F, -8.0F, -8.0F);
                GL11.glScalef(SECTION_SCALE, SECTION_SCALE, SECTION_SCALE);
                GL11.glTranslatef(8.0F, 8.0F, 8.0F);
                GL11.glDrawArrays(mesh.drawMode, 0, mesh.vertexCount);
                GL11.glPopMatrix();
                ++drawn;
            }
        }
        finally
        {
            if (brightnessOn)
            {
                Staging.clientActiveTexture(Staging.lightmapUnit());
                GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                Staging.clientActiveTexture(Staging.defaultUnit());
            }

            if (textureOn)
            {
                GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            }

            if (colorOn)
            {
                GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
            }

            if (normalsOn)
            {
                GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            }

            if (vertexOn)
            {
                GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            }

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }

        this.sectionsDrawn += drawn;
        this.drawCalls += drawn;
        this.drawNanos += System.nanoTime() - start;
    }

    @Override
    public void reset()
    {
        for (int i = 0; i < this.allBuffers.size(); ++i)
        {
            GL15.glDeleteBuffers(this.allBuffers.get(i).intValue());
        }

        this.allBuffers.clear();
        this.bufferBytes = 0L;
        ++this.generation;
    }

    @Override
    public long bufferBytes()
    {
        return this.bufferBytes;
    }

    @Override
    public long[] drainCounters()
    {
        long[] out = {this.uploads, this.uploadedBytes, this.uploadNanos,
            this.sectionsDrawn, this.drawCalls, this.drawNanos};
        this.uploads = 0L;
        this.uploadedBytes = 0L;
        this.uploadNanos = 0L;
        this.sectionsDrawn = 0L;
        this.drawCalls = 0L;
        this.drawNanos = 0L;
        return out;
    }

    private static void setClientState(int state, boolean on)
    {
        if (on)
        {
            GL11.glEnableClientState(state);
        }
        else
        {
            GL11.glDisableClientState(state);
        }
    }
}
