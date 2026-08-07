package vertex.benchmark;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import vertex.benchmark.plan.BenchmarkPlan;
import vertex.benchmark.plan.BenchmarkPlanIO;

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
            CliArguments parsed = CliArguments.parse(arguments);
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
        System.out.println("  java -jar vertex-benchmark.jar validate --plan <file>");
        System.out.println("  java -jar vertex-benchmark.jar run --plan <file> [--presentmon <file>] [--dry-run]");
        System.out.println("  java -jar vertex-benchmark.jar analyze --csv <file> [--metric presented|displayed|auto]");
    }

    private BenchmarkMain()
    {
    }
}
