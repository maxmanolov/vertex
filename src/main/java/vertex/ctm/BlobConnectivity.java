package vertex.ctm;

/**
 * Connectivity math for the standard CTM ("blob") method, derived from first principles:
 * a face's tile is chosen by which of its 8 in-plane neighbors connect, with the rule that
 * a corner neighbor is only meaningful when both edge neighbors adjacent to that corner
 * connect (otherwise the seam is already broken there and the corner cannot affect the
 * border shape). Masking irrelevant corners collapses the 256 raw neighbor combinations
 * into exactly 47 equivalence classes - the classic 47-tile blob tileset.
 *
 * Bit layout: 0=N, 1=E, 2=S, 3=W (edges), 4=NE, 5=SE, 6=SW, 7=NW (corners).
 * The class id returned is the canonical masked bitmask; mapping class ids to positions in
 * a pack's 47-tile atlas is a separate calibration table supplied with the render hook.
 */
public final class BlobConnectivity
{
    public static final int N = 1;
    public static final int E = 2;
    public static final int S = 4;
    public static final int W = 8;
    public static final int NE = 16;
    public static final int SE = 32;
    public static final int SW = 64;
    public static final int NW = 128;

    /** Canonical class for a raw 8-neighbor mask: irrelevant corner bits are cleared. */
    public static int canonical(int rawMask)
    {
        int edges = rawMask & (N | E | S | W);
        int corners = 0;

        if ((edges & (N | E)) == (N | E))
        {
            corners |= rawMask & NE;
        }

        if ((edges & (S | E)) == (S | E))
        {
            corners |= rawMask & SE;
        }

        if ((edges & (S | W)) == (S | W))
        {
            corners |= rawMask & SW;
        }

        if ((edges & (N | W)) == (N | W))
        {
            corners |= rawMask & NW;
        }

        return edges | corners;
    }

    /** Number of distinct canonical classes; must be 47 for the standard blob tileset. */
    public static int classCount()
    {
        java.util.HashSet<Integer> classes = new java.util.HashSet<Integer>();

        for (int mask = 0; mask < 256; ++mask)
        {
            classes.add(Integer.valueOf(canonical(mask)));
        }

        return classes.size();
    }

    private BlobConnectivity()
    {
    }
}
