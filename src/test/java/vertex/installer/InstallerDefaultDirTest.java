package vertex.installer;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
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

    private static IllegalArgumentException invalid(String... args) throws Exception
    {
        try
        {
            defaultDir(args);
        }
        catch (InvocationTargetException expected)
        {
            assertTrue(expected.getCause() instanceof IllegalArgumentException);
            return (IllegalArgumentException)expected.getCause();
        }

        throw new AssertionError("installer arguments were accepted");
    }

    @Test
    public void explicitMcdirArgumentWinsOnEveryPlatform() throws Exception
    {
        File dir = defaultDir(new String[] {"install", "--mcdir", "custom-target"});
        assertEquals("custom-target", dir.getName());
    }

    @Test
    public void missingMcdirValueRefusesTheDefaultDirectory() throws Exception
    {
        assertTrue(invalid("install", "--mcdir").getMessage().contains("requires a path"));
    }

    @Test
    public void emptyAndOptionLikeMcdirValuesAreRejected() throws Exception
    {
        assertTrue(invalid("install", "--mcdir", " ").getMessage().contains("non-empty path"));
        assertTrue(invalid("install", "--mcdir", "--other").getMessage().contains("non-empty path"));
    }

    @Test
    public void duplicateMcdirArgumentsAreRejected() throws Exception
    {
        assertTrue(invalid("install", "--mcdir", "first", "--mcdir", "second")
            .getMessage().contains("only once"));
    }

    @Test
    public void unknownAndExtraArgumentsAreRejected() throws Exception
    {
        assertTrue(invalid("install", "--mcdr", "target").getMessage().contains("unknown argument"));
        assertTrue(invalid("install", "extra").getMessage().contains("unknown argument"));
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
