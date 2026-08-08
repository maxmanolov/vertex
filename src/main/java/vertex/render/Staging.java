package vertex.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GLContext;

/**
 * Client-thread staging area for handing {@link MeshData} to GL: one grow-only direct
 * buffer in native byte order plus typed views, mirroring how the vanilla Tessellator
 * feeds glDrawArrays. Backends use it two ways: the display-list backend emits client
 * vertex arrays inside an open list (glDrawArrays dereferences at compile time), the
 * VBO backend uploads the staged bytes with glBufferData.
 *
 * Everything here is client-thread only, like all GL in Vertex.
 */
public final class Staging
{
    /** GL client-state and multitexture constants (GL13 core == ARB values). */
    private static final int GL_TEXTURE0 = 0x84C0;
    private static final int GL_TEXTURE1 = 0x84C1;

    private static ByteBuffer bytes;
    private static IntBuffer ints;
    private static FloatBuffer floats;
    private static ShortBuffer shorts;

    private static boolean multiTexResolved = false;
    private static boolean useCoreGL13 = false;

    /** Loads the mesh into the staging buffer; views are positioned at 0 with tight limits. */
    public static ByteBuffer load(MeshData mesh)
    {
        int usedInts = mesh.data.length;
        ensureCapacity(usedInts * 4);
        ints.clear();
        ints.put(mesh.data, 0, usedInts);
        bytes.position(0);
        bytes.limit(usedInts * 4);
        floats.position(0);
        floats.limit(usedInts);
        shorts.position(0);
        shorts.limit(usedInts * 2);
        return bytes;
    }

    /**
     * The vanilla Tessellator.draw() sequence (verified against bmh.a()I bytecode) fed
     * from the staged mesh: pointer setup per present attribute, one glDrawArrays, then
     * client-state teardown in the same order. Inside an open display list the arrays
     * are dereferenced at compile time, which is exactly how vanilla fills its lists.
     */
    public static void drawClientArrays(MeshData mesh)
    {
        load(mesh);

        if (mesh.hasTexture)
        {
            floats.position(3);
            GL11.glTexCoordPointer(2, MeshData.STRIDE, floats);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        }

        if (mesh.hasBrightness)
        {
            clientActiveTexture(GL_TEXTURE1);
            shorts.position(14);
            GL11.glTexCoordPointer(2, MeshData.STRIDE, shorts);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            clientActiveTexture(GL_TEXTURE0);
        }

        if (mesh.hasColor)
        {
            bytes.position(20);
            GL11.glColorPointer(4, true, MeshData.STRIDE, bytes);
            GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        }

        if (mesh.hasNormals)
        {
            bytes.position(24);
            GL11.glNormalPointer(MeshData.STRIDE, bytes);
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        }

        floats.position(0);
        GL11.glVertexPointer(3, MeshData.STRIDE, floats);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDrawArrays(mesh.drawMode, 0, mesh.vertexCount);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

        if (mesh.hasTexture)
        {
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        }

        if (mesh.hasBrightness)
        {
            clientActiveTexture(GL_TEXTURE1);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            clientActiveTexture(GL_TEXTURE0);
        }

        if (mesh.hasColor)
        {
            GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        }

        if (mesh.hasNormals)
        {
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        }
    }

    /**
     * Same decision vanilla's GL helper makes, from the public GL spec: prefer the GL 1.3
     * core entry point, fall back to ARB_multitexture (identical enum values).
     */
    public static void clientActiveTexture(int unit)
    {
        if (!multiTexResolved)
        {
            useCoreGL13 = GLContext.getCapabilities().OpenGL13;
            multiTexResolved = true;
        }

        if (useCoreGL13)
        {
            GL13.glClientActiveTexture(unit);
        }
        else
        {
            org.lwjgl.opengl.ARBMultitexture.glClientActiveTextureARB(unit);
        }
    }

    /** The second (lightmap) texture unit, for backends that manage their own pointers. */
    public static int lightmapUnit()
    {
        return GL_TEXTURE1;
    }

    /** The default texture unit. */
    public static int defaultUnit()
    {
        return GL_TEXTURE0;
    }

    private static void ensureCapacity(int byteCapacity)
    {
        if (bytes != null && bytes.capacity() >= byteCapacity)
        {
            return;
        }

        int capacity = bytes == null ? 1 << 20 : bytes.capacity();

        while (capacity < byteCapacity)
        {
            capacity <<= 1;
        }

        bytes = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
        ints = bytes.asIntBuffer();
        floats = bytes.asFloatBuffer();
        shorts = bytes.asShortBuffer();
    }

    private Staging()
    {
    }
}
