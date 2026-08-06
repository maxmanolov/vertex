package vertex.installer;

import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InstallerCopyTest
{
    @Test
    public void copyingAFileOntoItselfLeavesItIntact() throws Exception
    {
        File file = File.createTempFile("vertex-jar", ".jar");
        file.deleteOnExit();
        FileOutputStream out = new FileOutputStream(file);
        out.write(new byte[] {1, 2, 3, 4, 5});
        out.close();

        // The reporter's case: source and destination are the same path.
        VertexInstaller.copy(file, file);
        assertEquals(5L, file.length());

        // Same file reached through a relative-ish alternate path must also be detected.
        File alternate = new File(file.getParentFile(), "." + File.separator + file.getName());
        VertexInstaller.copy(alternate, file);
        assertEquals(5L, file.length());
    }

    @Test
    public void normalCopiesStillWork() throws Exception
    {
        File from = File.createTempFile("vertex-src", ".jar");
        File to = new File(from.getParentFile(), from.getName() + ".copy");
        from.deleteOnExit();
        to.deleteOnExit();
        FileOutputStream out = new FileOutputStream(from);
        out.write(new byte[] {9, 9, 9});
        out.close();
        VertexInstaller.copy(from, to);
        assertTrue(to.isFile());
        assertEquals(3L, to.length());
    }
}
