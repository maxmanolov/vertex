import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/** Standalone renderer-order microbenchmark: javac then java RendererSortBench. */
public final class RendererSortBench
{
    private static final int COUNT = 33 * 16 * 33;
    private static final int WARMUPS = 20;
    private static final int RUNS = 31;

    private static final class Section
    {
        final int x;
        final int y;
        final int z;
        double key;

        Section(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Recomputing implements Comparator<Section>
    {
        long evaluations;

        public int compare(Section left, Section right)
        {
            return Double.compare(distance(left), distance(right));
        }

        private double distance(Section value)
        {
            ++this.evaluations;
            double dx = value.x - 3.25D;
            double dy = value.y - 71.5D;
            double dz = value.z + 11.75D;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final Comparator<Section> CACHED = new Comparator<Section>()
    {
        public int compare(Section left, Section right)
        {
            return Double.compare(left.key, right.key);
        }
    };

    public static void main(String[] args)
    {
        Section[] shuffled = grid();
        shuffle(shuffled, 0x5EEDL);
        runScenario("shuffled", shuffled);

        Section[] nearlySorted = grid();
        Arrays.sort(nearlySorted, new Recomputing());

        for (int i = 97; i < nearlySorted.length; i += 97)
        {
            Section swap = nearlySorted[i - 1];
            nearlySorted[i - 1] = nearlySorted[i];
            nearlySorted[i] = swap;
        }

        runScenario("nearly-sorted", nearlySorted);
    }

    private static void runScenario(String name, Section[] source)
    {

        for (int i = 0; i < WARMUPS; ++i)
        {
            legacy(source);
            cached(source);
        }

        long[] legacy = new long[RUNS];
        long[] cached = new long[RUNS];
        long legacyEvaluations = 0L;

        for (int i = 0; i < RUNS; ++i)
        {
            Result old = legacy(source);
            Result next = cached(source);
            legacy[i] = old.nanos;
            cached[i] = next.nanos;
            legacyEvaluations = old.evaluations;
        }

        Arrays.sort(legacy);
        Arrays.sort(cached);
        System.out.printf("scenario=%s sections=%d legacyMedianMs=%.3f cachedMedianMs=%.3f speedup=%.2fx%n",
            name, COUNT, ms(legacy[RUNS / 2]), ms(cached[RUNS / 2]),
            (double)legacy[RUNS / 2] / (double)cached[RUNS / 2]);
        System.out.printf("distanceEvaluations legacy=%d cached=%d reduction=%.1fx%n",
            legacyEvaluations, COUNT, (double)legacyEvaluations / (double)COUNT);
    }

    private static Result legacy(Section[] source)
    {
        Section[] copy = source.clone();
        Recomputing comparator = new Recomputing();
        long start = System.nanoTime();
        Arrays.sort(copy, comparator);
        return new Result(System.nanoTime() - start, comparator.evaluations);
    }

    private static Result cached(Section[] source)
    {
        Section[] copy = source.clone();
        long start = System.nanoTime();

        for (Section value : copy)
        {
            double dx = value.x - 3.25D;
            double dy = value.y - 71.5D;
            double dz = value.z + 11.75D;
            value.key = dx * dx + dy * dy + dz * dz;
        }

        Arrays.sort(copy, CACHED);
        return new Result(System.nanoTime() - start, COUNT);
    }

    private static Section[] grid()
    {
        Section[] out = new Section[COUNT];
        int index = 0;

        for (int x = -16; x <= 16; ++x)
        {
            for (int y = 0; y < 16; ++y)
            {
                for (int z = -16; z <= 16; ++z)
                {
                    out[index++] = new Section(x * 16 + 8, y * 16 + 8, z * 16 + 8);
                }
            }
        }

        return out;
    }

    private static void shuffle(Section[] values, long seed)
    {
        Random random = new Random(seed);

        for (int i = values.length - 1; i > 0; --i)
        {
            int j = random.nextInt(i + 1);
            Section swap = values[i];
            values[i] = values[j];
            values[j] = swap;
        }
    }

    private static double ms(long nanos)
    {
        return nanos / 1_000_000.0D;
    }

    private static final class Result
    {
        final long nanos;
        final long evaluations;

        Result(long nanos, long evaluations)
        {
            this.nanos = nanos;
            this.evaluations = evaluations;
        }
    }
}
