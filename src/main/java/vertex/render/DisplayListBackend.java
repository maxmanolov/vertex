package vertex.render;

import java.util.List;
import org.lwjgl.opengl.GL11;

/**
 * Compatibility backend: GPU representation stays the vanilla per-section display list
 * and submission stays vanilla's RenderList/glCallLists batching, but geometry arrives
 * as {@link MeshData} through the one shared upload path. This proves the section-mesh
 * pipeline end to end (workers produce data, the client thread owns every GL call) with
 * bit-equivalent visuals, so the VBO and arena backends are a swap of this class, not
 * another renderer surgery.
 *
 * The compiled list reproduces the exact interior of a vanilla-built list, verified
 * against blo.b(I)V / blo.a(ILsv;)V bytecode: clip-space translation, the 1.000001
 * anti-crack scale about the section center, then the dereferenced vertex arrays.
 */
public final class DisplayListBackend implements RenderBackend
{
    private static final float SECTION_SCALE = 1.000001F;

    private long uploads = 0L;
    private long uploadedBytes = 0L;
    private long uploadNanos = 0L;

    @Override
    public String name()
    {
        return "displaylist";
    }

    @Override
    public void upload(Object renderer, int pass, MeshData mesh, int originX, int originY, int originZ, int glListBase)
    {
        long start = System.nanoTime();
        // The same bracket discipline as the multicore replay (#74): an exception between
        // glNewList and glEndList would swallow every later GL call into the open list,
        // and an unmatched push corrupts the matrix stack.
        boolean listOpen = false;
        boolean matrixPushed = false;

        try
        {
            GL11.glNewList(glListBase + pass, GL11.GL_COMPILE);
            listOpen = true;
            GL11.glPushMatrix();
            matrixPushed = true;
            // setupGLTranslation equivalent: x/z clip to the 1024 grid, y unclipped.
            GL11.glTranslatef((float)(originX & 1023), (float)originY, (float)(originZ & 1023));
            GL11.glTranslatef(-8.0F, -8.0F, -8.0F);
            GL11.glScalef(SECTION_SCALE, SECTION_SCALE, SECTION_SCALE);
            GL11.glTranslatef(8.0F, 8.0F, 8.0F);

            if (!mesh.isEmpty())
            {
                Staging.drawClientArrays(mesh);
            }
        }
        finally
        {
            if (matrixPushed)
            {
                GL11.glPopMatrix();
            }

            if (listOpen)
            {
                GL11.glEndList();
            }
        }

        ++this.uploads;
        this.uploadedBytes += mesh.byteSize();
        this.uploadNanos += System.nanoTime() - start;
    }

    @Override
    public boolean ownsSubmission()
    {
        // Vanilla RenderList batching keeps drawing the lists this backend compiles.
        return false;
    }

    @Override
    public void drawVisible(List<?> sections, int pass, double camX, double camY, double camZ)
    {
        throw new UnsupportedOperationException("display-list submission stays with vanilla");
    }

    @Override
    public void reset()
    {
        // List ids belong to vanilla's grid allocation; nothing of ours to release.
    }

    @Override
    public long bufferBytes()
    {
        // Display-list memory lives in the driver and is not accountable from here.
        return 0L;
    }

    @Override
    public long[] drainCounters()
    {
        long[] out = {this.uploads, this.uploadedBytes, this.uploadNanos, 0L, 0L, 0L};
        this.uploads = 0L;
        this.uploadedBytes = 0L;
        this.uploadNanos = 0L;
        return out;
    }
}
