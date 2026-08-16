import java.util.ArrayList;
import java.util.List;

/** Models the two mapped RD16 renderer-array walks at the measured active ratio. */
public final class ActiveSectionWalkBench
{
    private static final int FULL = 17424;
    private static final int ACTIVE = 4367;
    private static final int ROUNDS = 20000;
    private static final int SAMPLES = 9;

    public static void main(String[] args)
    {
        Section[] full = new Section[FULL];
        List<Section> live = new ArrayList<Section>(ACTIVE);

        for (int i = 0; i < full.length; ++i)
        {
            // Spread exactly ACTIVE entries through the full array instead of putting
            // them in one branch-predictor-friendly prefix.
            boolean active = (long)(i + 1) * ACTIVE / FULL != (long)i * ACTIVE / FULL;
            full[i] = new Section(i, active, (i & 3) != 0);

            if (active)
            {
                live.add(full[i]);
            }
        }

        Section[] compact = live.toArray(new Section[live.size()]);

        for (int i = 0; i < 2000; ++i)
        {
            keep(walk(full));
            keep(walk(compact));
        }

        long[] fullTimes = new long[SAMPLES];
        long[] compactTimes = new long[SAMPLES];

        for (int sample = 0; sample < SAMPLES; ++sample)
        {
            if ((sample & 1) == 0)
            {
                fullTimes[sample] = measure(full);
                compactTimes[sample] = measure(compact);
            }
            else
            {
                compactTimes[sample] = measure(compact);
                fullTimes[sample] = measure(full);
            }
        }

        double fullNs = median(fullTimes) / (double)ROUNDS;
        double compactNs = median(compactTimes) / (double)ROUNDS;
        System.out.printf("full=%d active=%d fullWalkNs=%.1f activeWalkNs=%.1f speedup=%.2fx%n",
            FULL, ACTIVE, fullNs, compactNs, fullNs / compactNs);
    }

    private static long measure(Section[] sections)
    {
        long start = System.nanoTime();
        int result = 0;

        for (int i = 0; i < ROUNDS; ++i)
        {
            result += walk(sections);
        }

        keep(result);
        return System.nanoTime() - start;
    }

    /** Empty test plus visibility/pass classification shared by clipping and traversal. */
    private static int walk(Section[] sections)
    {
        int result = 0;

        for (Section section : sections)
        {
            if (section.active && section.visible)
            {
                result += section.id;
            }
        }

        return result;
    }

    private static long median(long[] values)
    {
        java.util.Arrays.sort(values);
        return values[values.length / 2];
    }

    private static volatile int sink;

    private static void keep(int value)
    {
        sink = value;
    }

    private static final class Section
    {
        final int id;
        final boolean active;
        final boolean visible;

        Section(int id, boolean active, boolean visible)
        {
            this.id = id;
            this.active = active;
            this.visible = visible;
        }
    }
}
