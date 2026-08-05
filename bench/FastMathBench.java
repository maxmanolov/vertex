import java.util.Random;

/**
 * Settles the Fast Math question with numbers: vanilla 1.7.10 trig uses a 65536-entry
 * float sine table indexed by (int)(rad * 10430.378f) & 0xFFFF; the OptiFine-popularized
 * "fast math" idea shrinks the table to 4096 entries for cache friendliness at reduced
 * accuracy. This measures both on the current hardware with coherent (game-like) and
 * random access patterns, plus worst-case error, so FEATURES.md can record a data-backed
 * adopt/reject decision. Run: javac FastMathBench.java && java -Xmx256m FastMathBench
 */
public final class FastMathBench
{
    private static final float[] BIG = new float[65536];
    private static final float[] SMALL = new float[4096];

    static
    {
        for (int i = 0; i < BIG.length; ++i)
        {
            BIG[i] = (float)Math.sin((double)i * Math.PI * 2.0D / 65536.0D);
        }

        for (int i = 0; i < SMALL.length; ++i)
        {
            SMALL[i] = (float)Math.sin((double)i * Math.PI * 2.0D / 4096.0D);
        }
    }

    private static float sinBig(float rad)
    {
        return BIG[(int)(rad * 10430.378F) & 0xFFFF];
    }

    private static float sinSmall(float rad)
    {
        return SMALL[(int)(rad * 651.8986F) & 0xFFF];
    }

    public static void main(String[] args)
    {
        float[] coherent = new float[1 << 20];
        float[] random = new float[1 << 20];
        Random rng = new Random(42L);

        for (int i = 0; i < coherent.length; ++i)
        {
            coherent[i] = i * 0.01F;
            random[i] = rng.nextFloat() * 6283.0F;
        }

        for (int warm = 0; warm < 3; ++warm)
        {
            run("warmup", coherent, false);
        }

        System.out.println("pattern      table   ns/op");
        run("coherent", coherent, true);
        run("random  ", random, true);
        double worstBig = 0.0D;
        double worstSmall = 0.0D;

        for (float a = 0.0F; a < 6.2832F; a += 0.0001F)
        {
            worstBig = Math.max(worstBig, Math.abs(Math.sin(a) - sinBig(a)));
            worstSmall = Math.max(worstSmall, Math.abs(Math.sin(a) - sinSmall(a)));
        }

        System.out.println();
        System.out.printf("worst-case abs error  64k: %.6f   4k: %.6f%n", worstBig, worstSmall);
    }

    private static void run(String label, float[] input, boolean print)
    {
        float sinkBig = 0.0F;
        long t0 = System.nanoTime();

        for (int rep = 0; rep < 20; ++rep)
        {
            for (float value : input)
            {
                sinkBig += sinBig(value);
            }
        }

        long tBig = System.nanoTime() - t0;
        float sinkSmall = 0.0F;
        t0 = System.nanoTime();

        for (int rep = 0; rep < 20; ++rep)
        {
            for (float value : input)
            {
                sinkSmall += sinSmall(value);
            }
        }

        long tSmall = System.nanoTime() - t0;
        long ops = 20L * input.length;

        if (print)
        {
            System.out.printf("%s     64k    %.3f%n", label, (double)tBig / ops);
            System.out.printf("%s     4k     %.3f%n", label, (double)tSmall / ops);
        }

        if (sinkBig == 123.456F && sinkSmall == 654.321F)
        {
            System.out.println("(unreachable sink)");
        }
    }

    private FastMathBench()
    {
    }
}
