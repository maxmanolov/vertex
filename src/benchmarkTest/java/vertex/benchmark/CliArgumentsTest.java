package vertex.benchmark;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CliArgumentsTest
{
    @Test
    public void keepsAnOptionWithSpacesAsOneValue()
    {
        CliArguments parsed = CliArguments.parse(new String[] {
            "run", "--plan", "C:\\Bench Plans\\clients.json", "--dry-run"
        });

        assertEquals("run", parsed.getCommand());
        assertEquals("C:\\Bench Plans\\clients.json", parsed.require("plan"));
        assertTrue(parsed.flag("dry-run"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnOptionWithoutAValue()
    {
        CliArguments.parse(new String[] {"run", "--plan"});
    }

    @Test
    public void retainsDroppedPathsAndQuickOptions()
    {
        CliArguments parsed = CliArguments.parse(new String[] {
            "quick",
            "C:\\Client Jars\\vanilla 1.7.10.jar",
            "D:\\Clients\\Vertex.jar",
            "--mcdir", "C:\\Games\\Minecraft",
            "--presentmon", "C:\\Tools\\PresentMon.exe",
            "--preset", "standard",
            "--no-open"
        });

        assertEquals(2, parsed.getPositionals().size());
        assertEquals("C:\\Client Jars\\vanilla 1.7.10.jar",
            parsed.getPositionals().get(0));
        assertEquals("D:\\Clients\\Vertex.jar", parsed.getPositionals().get(1));
        assertEquals("C:\\Games\\Minecraft", parsed.require("mcdir"));
        assertEquals("C:\\Tools\\PresentMon.exe", parsed.require("presentmon"));
        assertEquals("standard", parsed.require("preset"));
        assertTrue(parsed.flag("no-open"));
        assertFalse(parsed.flag("dry-run"));
    }

    @Test
    public void endsOptionParsingBeforeAPathThatStartsWithDashes()
    {
        CliArguments parsed = CliArguments.parse(new String[] {
            "quick", "--", "--test-client.jar"
        });

        assertEquals(1, parsed.getPositionals().size());
        assertEquals("--test-client.jar", parsed.getPositionals().get(0));
    }
}
