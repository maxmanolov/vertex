package vertex.benchmark;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import vertex.benchmark.plan.BenchmarkPlan;
import vertex.benchmark.plan.BenchmarkPlanIO;
import vertex.benchmark.quick.QuickBenchmark;

/** Entry point for the standalone local benchmark harness. */
public final class BenchmarkMain
{
    public static void main(String[] arguments)
    {
        int exitCode = execute(arguments);

        if (exitCode != 0)
        {
            System.exit(exitCode);
        }
    }

    static int execute(String[] arguments)
    {
        try
        {
            CliArguments parsed = CliArguments.parse(normalize(arguments));
            String command = parsed.getCommand();

            if (command.equals("help") || parsed.flag("help"))
            {
                usage();
                return 0;
            }

            if (command.equals("validate"))
            {
                BenchmarkPlan plan = load(parsed.require("plan"));
                System.out.println("Plan is valid: " + plan.getSuiteId());
                printWarnings(FairnessChecker.check(plan));
                return 0;
            }

            if (command.equals("analyze"))
            {
                Path csv = Paths.get(parsed.require("csv"));
                CaptureAnalyzer.analyze(csv, CaptureAnalyzer.preference(parsed.option("metric")));
                return 0;
            }

            if (command.equals("run"))
            {
                Path planPath = Paths.get(parsed.require("plan")).toAbsolutePath().normalize();
                BenchmarkPlan plan = load(planPath.toString());
                new BenchmarkRunner().run(planPath, plan, parsed.option("presentmon"),
                    parsed.flag("dry-run"));
                return 0;
            }

            if (command.equals("quick"))
            {
                new QuickBenchmark().run(parsed);
                return 0;
            }

            throw new IllegalArgumentException("Unknown command: " + command);
        }
        catch (Exception error)
        {
            System.err.println("Benchmark error: " + error.getMessage());
            return 2;
        }
    }

    private static BenchmarkPlan load(String path) throws Exception
    {
        return BenchmarkPlanIO.loadResolved(Paths.get(path), System.getenv());
    }

    private static void printWarnings(List<String> warnings)
    {
        for (String warning : warnings)
        {
            System.out.println("Warning: " + warning);
        }
    }

    private static void usage()
    {
        System.out.println("Vertex local client benchmark");
        System.out.println("Usage:");
        System.out.println("  java -jar vertex-benchmark.jar quick [client.jar ...] [--preset fast|standard]");
        System.out.println("      [--mcdir <directory>] [--no-open] [--dry-run]");
        System.out.println("  java -jar vertex-benchmark.jar validate --plan <file>");
        System.out.println("  java -jar vertex-benchmark.jar run --plan <file> [--presentmon <file>] [--dry-run]");
        System.out.println("  java -jar vertex-benchmark.jar analyze --csv <file> [--metric presented|displayed|auto]");
    }

    private static String[] normalize(String[] arguments)
    {
        if (arguments.length == 0)
        {
            return new String[] {"quick"};
        }

        if (isCommand(arguments[0]))
        {
            return arguments;
        }

        try
        {
            if (!Files.exists(Paths.get(arguments[0])))
            {
                return arguments;
            }
        }
        catch (RuntimeException invalidPath)
        {
            return arguments;
        }

        List<String> values = new ArrayList<String>();
        values.add("quick");
        values.addAll(Arrays.asList(arguments));
        return values.toArray(new String[values.size()]);
    }

    private static boolean isCommand(String value)
    {
        return value.equals("help") || value.equals("validate") || value.equals("analyze")
            || value.equals("run") || value.equals("quick");
    }

    private BenchmarkMain()
    {
    }
}
