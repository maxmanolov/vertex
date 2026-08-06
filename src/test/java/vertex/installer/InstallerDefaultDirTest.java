package vertex.installer;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Asserts the installer's default .minecraft location on whichever OS the test actually
 * runs on - meaningful on all three CI legs (Linux, Windows, macOS), since the Windows
 * and macOS branches previously shipped without ever executing on those platforms.
 */
public class InstallerDefaultDirTest
{
    private static File defaultDir(String[] args) throws Exception
    {
        Method minecraftDir = VertexInstaller.class.getDeclaredMethod("minecraftDir", String[].class);
        minecraftDir.setAccessible(true);
        return (File) minecraftDir.invoke(null, (Object) args);
    }

    @Test
    public void explicitMcdirArgumentWinsOnEveryPlatform() throws Exception
    {
        File dir = defaultDir(new String[] {"install", "--mcdir", "custom-target"});
        assertEquals("custom-target", dir.getName());
    }

    @Test
    public void defaultLocationMatchesThePlatformConvention() throws Exception
    {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String path = defaultDir(new String[0]).getPath();

        if (os.contains("win"))
        {
            assertTrue("Windows default should end in .minecraft under APPDATA: " + path,
                path.endsWith(".minecraft") && System.getenv("APPDATA") != null
                    && path.startsWith(System.getenv("APPDATA")));
        }
        else if (os.contains("mac"))
        {
            assertTrue("macOS default should live in Application Support: " + path,
                path.endsWith("Library/Application Support/minecraft"));
        }
        else
        {
            assertTrue("Linux default should be ~/.minecraft: " + path,
                path.endsWith(".minecraft"));
        }
    }
}
