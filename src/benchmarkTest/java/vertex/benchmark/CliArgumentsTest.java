package vertex.benchmark;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
}
