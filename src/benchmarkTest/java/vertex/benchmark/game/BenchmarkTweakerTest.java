package vertex.benchmark.game;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class BenchmarkTweakerTest
{
    @After
    public void clearProperties()
    {
        System.clearProperty("vertex.benchmark.delegateArguments");
    }

    @Test
    public void returnsClientArgumentsForVanilla()
    {
        BenchmarkTweaker tweaker = new BenchmarkTweaker();
        tweaker.acceptOptions(Arrays.asList("--username", "Benchmark"),
            new File("game"), new File("assets"), "1.7.10-benchmark");
        List<String> result = Arrays.asList(tweaker.getLaunchArguments());

        assertTrue(result.contains("--username"));
        assertTrue(result.contains("--version"));
        assertTrue(result.contains("--gameDir"));
        assertTrue(result.contains("--assetsDir"));
    }

    @Test
    public void delegatesClientArgumentsToCandidateTweaker()
    {
        System.setProperty("vertex.benchmark.delegateArguments", "true");
        BenchmarkTweaker tweaker = new BenchmarkTweaker();
        tweaker.acceptOptions(Arrays.asList("--username", "Benchmark"),
            new File("game"), new File("assets"), "1.7.10-benchmark");

        assertTrue(tweaker.getLaunchArguments().length == 0);
    }
}
