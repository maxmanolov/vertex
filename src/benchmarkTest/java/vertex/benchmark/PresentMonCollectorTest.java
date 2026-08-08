package vertex.benchmark;

import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PresentMonCollectorTest
{
    @Test
    public void keepsPathsWithSpacesAsSingleArguments()
    {
        Path output = Paths.get("Benchmark Results", "frames.csv");
        List<String> command = PresentMonCollector.buildCommand(
            "C:\\Capture Tools\\PresentMon.exe", 4812L, 120,
            output, "VertexBench_1");

        assertEquals("C:\\Capture Tools\\PresentMon.exe", command.get(0));
        assertTrue(command.contains(output.toAbsolutePath().toString()));
        assertEquals("4812", command.get(command.indexOf("--process_id") + 1));
        assertEquals("120", command.get(command.indexOf("--timed") + 1));
    }
}
