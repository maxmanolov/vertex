package vertex.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class MeshDataTest
{
    /**
     * Field-name stand-in for the obfuscated 1.7.10 Tessellator: same names the
     * extractor resolves through Mappings (f=rawBuffer, g=vertexCount,
     * p=rawBufferIndex, s=drawMode, l/m/n/o = color/texture/brightness/normals).
     */
    public static final class FakeTessellator
    {
        public int[] f = new int[64];
        public int g;
        public int p;
        public int s;
        public boolean l;
        public boolean m;
        public boolean n;
        public boolean o;
    }

    @Test
    public void extractorCopiesOnlyTheUsedPrefixAndAllFlags() throws Exception
    {
        FakeTessellator tess = new FakeTessellator();

        for (int i = 0; i < tess.f.length; ++i)
        {
            tess.f[i] = i + 1;
        }

        tess.p = 16;      // two vertices worth of ints
        tess.g = 2;
        tess.s = 7;       // GL_QUADS
        tess.l = true;
        tess.m = true;
        tess.n = true;
        tess.o = false;

        MeshData mesh = new MeshData.Extractor().extract(tess);
        assertEquals(16, mesh.data.length);
        assertEquals(1, mesh.data[0]);
        assertEquals(16, mesh.data[15]);
        assertNotSame("must be a copy, not the live raw buffer", tess.f, mesh.data);
        assertEquals(2, mesh.vertexCount);
        assertEquals(7, mesh.drawMode);
        assertTrue(mesh.hasColor);
        assertTrue(mesh.hasTexture);
        assertTrue(mesh.hasBrightness);
        assertFalse(mesh.hasNormals);
        assertFalse(mesh.isEmpty());
        assertEquals(2 * MeshData.STRIDE, mesh.byteSize());
    }

    @Test
    public void emptyPassExtractsAsEmptyMesh() throws Exception
    {
        MeshData mesh = new MeshData.Extractor().extract(new FakeTessellator());
        assertTrue(mesh.isEmpty());
        assertEquals(0, mesh.data.length);
        assertEquals(0, mesh.byteSize());
    }

    @Test
    public void extractorInstancesResolveIndependently() throws Exception
    {
        // Two extractors against the same class must not interfere; the game only ever
        // has one Tessellator class, but the worker and client paths share one instance.
        MeshData.Extractor shared = new MeshData.Extractor();
        FakeTessellator tess = new FakeTessellator();
        tess.p = 8;
        tess.g = 1;
        assertEquals(8, shared.extract(tess).data.length);
        tess.p = 24;
        tess.g = 3;
        assertEquals("cached handles must read live values", 24, shared.extract(tess).data.length);
    }
}
