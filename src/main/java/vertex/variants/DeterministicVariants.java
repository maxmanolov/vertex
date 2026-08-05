package vertex.variants;

/**
 * Stable pseudo-random selection shared by natural textures (per block position and face)
 * and random entities (per entity id). The requirement is determinism, not cryptography:
 * the same world coordinate must pick the same variant on every rebuild, every session,
 * every machine, or seams flicker across chunk rebuilds. A splitmix-style avalanche over
 * the packed inputs gives uniform spread with zero allocation.
 */
public final class DeterministicVariants
{
    public static int hash(int x, int y, int z, int salt)
    {
        long key = x * 341873128712L + z * 132897987541L + y * 2971215073L + salt;
        key = (key ^ key >>> 30) * 0xBF58476D1CE4E5B9L;
        key = (key ^ key >>> 27) * 0x94D049BB133111EBL;
        return (int)(key ^ key >>> 31);
    }

    /** Uniform pick in [0, count). */
    public static int pick(int hash, int count)
    {
        return count <= 1 ? 0 : (hash >>> 1) % count;
    }

    /** Weighted pick; weights must be positive. Returns the selected index. */
    public static int pickWeighted(int hash, int[] weights)
    {
        int total = 0;

        for (int weight : weights)
        {
            total += weight;
        }

        int roll = (hash >>> 1) % total;

        for (int i = 0; i < weights.length; ++i)
        {
            roll -= weights[i];

            if (roll < 0)
            {
                return i;
            }
        }

        return weights.length - 1;
    }

    private DeterministicVariants()
    {
    }
}
