package vertex.natural;

/**
 * Variant decode for natural textures, constrained by what the vanilla IIcon surface can
 * express. IIcon exposes min/max U and V (plus interpolated lookups) and nothing that can
 * swap the U and V axes, so an icon proxy can mirror either axis - which yields identity,
 * horizontal mirror, vertical mirror, and 180 degree rotation (both axes) - but cannot
 * produce 90 or 270 degree rotation. Packs declaring 4 rotations therefore receive the
 * 2-rotation treatment; see FEATURES.md for the documented justification.
 *
 * Variant bit 0: rotate 180 (mirror both axes). Bit 1: horizontal mirror.
 */
public final class NaturalVariants
{
    public static int variantCount(int rotations, boolean flip)
    {
        int expressible = rotations >= 2 ? 2 : 1;
        return expressible * (flip ? 2 : 1);
    }

    public static boolean flipU(int variant)
    {
        return ((variant & 1) != 0) ^ ((variant & 2) != 0);
    }

    public static boolean flipV(int variant)
    {
        return (variant & 1) != 0;
    }

    private NaturalVariants()
    {
    }
}
