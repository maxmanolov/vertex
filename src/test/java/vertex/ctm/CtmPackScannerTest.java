package vertex.ctm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CtmPackScannerTest
{
    @Test
    public void scansDirectoryAndZipPacksAndIndexesRules() throws Exception
    {
        File root = temp();
        File dirPack = new File(root, "dirpack/assets/minecraft/mcpatcher/ctm/nested");
        dirPack.mkdirs();
        write(new File(dirPack, "glass.properties"), "method=ctm\nmatchTiles=glass\ntiles=0-46\n");
        write(new File(dirPack.getParentFile(), "shelf.properties"), "method=horizontal\nmatchBlocks=47\ntiles=0-3\n");
        zipPack(new File(root, "zippack.zip"), "assets/minecraft/mcpatcher/ctm/sandstone.properties",
            "method=ctm\nmatchTiles=sandstone\ntiles=0-46\n");

        List<String> skipped = new ArrayList<String>();
        CtmRuleSet rules = CtmPackScanner.scan(root, new String[] {"dirpack", "zippack.zip"}, recorder(skipped));

        assertEquals(3, rules.size());
        assertNotNull("nested directory rules must be found", rules.forTile("glass"));
        assertNotNull(rules.forTile("sandstone"));
        assertNotNull(rules.forBlock("47"));
        assertNull(rules.forTile("absent"));
        assertTrue(skipped.isEmpty());
    }

    @Test
    public void oneBadFileDoesNotCostThePackItsOtherRules() throws Exception
    {
        File root = temp();
        File ctm = new File(root, "pack/assets/minecraft/mcpatcher/ctm");
        ctm.mkdirs();
        write(new File(ctm, "good.properties"), "method=ctm\nmatchTiles=glass\ntiles=0-46\n");
        write(new File(ctm, "bad.properties"), "method=spaghetti\nmatchTiles=stone\n");

        List<String> skipped = new ArrayList<String>();
        CtmRuleSet rules = CtmPackScanner.scan(root, new String[] {"pack"}, recorder(skipped));

        assertEquals(1, rules.size());
        assertNotNull(rules.forTile("glass"));
        assertEquals(1, skipped.size());
    }

    @Test
    public void missingPacksAndNullsAreHarmless()
    {
        assertTrue(CtmPackScanner.scan(null, new String[] {"x"}, null).isEmpty());
        assertTrue(CtmPackScanner.scan(new File("/nonexistent-vertex-test"), new String[] {"x"}, null).isEmpty());
    }

    private static CtmPackScanner.Problem recorder(final List<String> into)
    {
        return new CtmPackScanner.Problem()
        {
            public void skipped(String source, Exception cause)
            {
                into.add(source);
            }
        };
    }

    private static File temp() throws IOException
    {
        File dir = File.createTempFile("vertex-ctm", "");
        dir.delete();
        dir.mkdirs();
        dir.deleteOnExit();
        return dir;
    }

    private static void write(File file, String content) throws IOException
    {
        FileOutputStream out = new FileOutputStream(file);
        out.write(content.getBytes("UTF-8"));
        out.close();
    }

    private static void zipPack(File zipFile, String entryName, String content) throws IOException
    {
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile));
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content.getBytes("UTF-8"));
        zip.closeEntry();
        zip.close();
    }
}
