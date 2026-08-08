package vertex.hooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Locks the six pages to the reference screenshots: inventory, ordering, wiring kinds
 * and geometry. The reference is transcribed here once; any layout drift fails loud.
 */
public final class VideoMenuLayoutTest
{
    private static final int W = 400;
    private static final int H = 300;
    private static final int LEFT = W / 2 - 155;
    private static final int RIGHT = W / 2 + 5;

    @Test
    public void videoPageMatchesTheReferenceIncludingTheShadersSlot()
    {
        List<VideoMenuLayout.Placed> page = VideoMenuLayout.layout(VideoMenuLayout.PAGE_VIDEO, W, H);
        assertEquals("Video Settings", VideoMenuLayout.title(VideoMenuLayout.PAGE_VIDEO));
        assertEquals(9 + 9 + 1, page.size());

        // Left column: Graphics, Smooth Lighting, SL Level (inert), GUI Scale,
        // Brightness, Dynamic Lights, Fullbright (the Shaders... slot), Details,
        // Animations.
        assertColumn(page, LEFT, new int[] {
            VideoMenuLayout.KIND_VANILLA, VideoMenuLayout.KIND_VANILLA,
            VideoMenuLayout.KIND_STATIC, VideoMenuLayout.KIND_VANILLA,
            VideoMenuLayout.KIND_SLIDER, VideoMenuLayout.KIND_VERTEX,
            VideoMenuLayout.KIND_FULLBRIGHT, VideoMenuLayout.KIND_NAV,
            VideoMenuLayout.KIND_NAV});
        // The Fullbright toggle occupies exactly the reference Shaders... rectangle:
        // left column, row 6.
        VideoMenuLayout.Placed fullbright = at(page, LEFT, VideoMenuLayout.rowY(H, 6));
        assertEquals(VideoMenuLayout.KIND_FULLBRIGHT, fullbright.kind);
        assertEquals(150, fullbright.width);
        assertEquals("the GUI probe clicks fullbright by this id", 406, fullbright.id);

        assertColumn(page, RIGHT, new int[] {
            VideoMenuLayout.KIND_SLIDER, VideoMenuLayout.KIND_SLIDER,
            VideoMenuLayout.KIND_VANILLA, VideoMenuLayout.KIND_VANILLA,
            VideoMenuLayout.KIND_CHUNK_LOADING, VideoMenuLayout.KIND_STATIC,
            VideoMenuLayout.KIND_NAV, VideoMenuLayout.KIND_NAV, VideoMenuLayout.KIND_NAV});

        assertEquals("f", at(page, RIGHT, VideoMenuLayout.rowY(H, 0)).ref); // render distance
        assertEquals("d", at(page, LEFT, VideoMenuLayout.rowY(H, 4)).ref);  // brightness

        // Navigation ids are stable for the GUI probe: 300 + target page.
        for (VideoMenuLayout.Placed slot : page)
        {
            if (slot.kind == VideoMenuLayout.KIND_NAV)
            {
                assertEquals(VideoMenuLayout.ID_NAV_BASE + Integer.parseInt(slot.ref), slot.id);
            }
        }

        assertDone(page, W / 2 - 100, 200);
    }

    @Test
    public void animationsPageHasTheSplitBottomRowAndOnlyRealToggles()
    {
        List<VideoMenuLayout.Placed> page =
            VideoMenuLayout.layout(VideoMenuLayout.PAGE_ANIMATIONS, W, H);
        assertEquals(9 + 9 + 3, page.size());

        VideoMenuLayout.Placed allOn = byKind(page, VideoMenuLayout.KIND_ALL_ON);
        VideoMenuLayout.Placed allOff = byKind(page, VideoMenuLayout.KIND_ALL_OFF);
        VideoMenuLayout.Placed done = byKind(page, VideoMenuLayout.KIND_DONE);
        assertEquals(W / 2 - 155, allOn.x);
        assertEquals(73, allOn.width);
        assertEquals(W / 2 - 78, allOff.x);
        assertEquals(73, allOff.width);
        assertEquals(W / 2 + 5, done.x);
        assertEquals(150, done.width);
        assertEquals(VideoMenuLayout.bottomRowY(H, 9), allOn.y);
        assertEquals(VideoMenuLayout.bottomRowY(H, 9), done.y);

        // Real backing on this page: void particles + terrain animations (Vertex keys)
        // and vanilla Particles; everything else is a disabled reference slot.
        assertEquals(2, kindCount(page, VideoMenuLayout.KIND_VERTEX));
        assertEquals(1, kindCount(page, VideoMenuLayout.KIND_VANILLA));
        assertEquals("q", byKind(page, VideoMenuLayout.KIND_VANILLA).ref);
        assertEquals(15, kindCount(page, VideoMenuLayout.KIND_STATIC));
    }

    @Test
    public void performancePageIsAllReferenceGeometryNoFakeBehavior()
    {
        List<VideoMenuLayout.Placed> page =
            VideoMenuLayout.layout(VideoMenuLayout.PAGE_PERFORMANCE, W, H);
        assertEquals(5 + 4 + 1, page.size());

        for (VideoMenuLayout.Placed slot : page)
        {
            assertTrue("performance slots have no real backing and must be inert",
                slot.kind == VideoMenuLayout.KIND_STATIC
                    || slot.kind == VideoMenuLayout.KIND_DONE);
        }
    }

    @Test
    public void detailsAndQualityWireExactlyTheBackedSlots()
    {
        List<VideoMenuLayout.Placed> details =
            VideoMenuLayout.layout(VideoMenuLayout.PAGE_DETAILS, W, H);
        assertEquals(9 + 8 + 1, details.size());
        assertEquals(3, kindCount(details, VideoMenuLayout.KIND_VERTEX));   // sky, fog, weather
        assertEquals(2, kindCount(details, VideoMenuLayout.KIND_VANILLA)); // clouds, capes
        assertEquals(1, kindCount(details, VideoMenuLayout.KIND_HELD_TOOLTIPS));
        assertEquals("p", at(details, LEFT, VideoMenuLayout.rowY(H, 0)).ref);

        List<VideoMenuLayout.Placed> quality =
            VideoMenuLayout.layout(VideoMenuLayout.PAGE_QUALITY, W, H);
        assertEquals(8 + 7 + 1, quality.size());
        assertEquals(2, kindCount(quality, VideoMenuLayout.KIND_SLIDER));  // mipmap, aniso
        assertEquals("F", at(quality, LEFT, VideoMenuLayout.rowY(H, 0)).ref);
        assertEquals("G", at(quality, LEFT, VideoMenuLayout.rowY(H, 1)).ref);
        assertEquals(5, kindCount(quality, VideoMenuLayout.KIND_VERTEX));
    }

    @Test
    public void otherPageCentersResetAboveDone()
    {
        List<VideoMenuLayout.Placed> page = VideoMenuLayout.layout(VideoMenuLayout.PAGE_OTHER, W, H);
        assertEquals(5 + 4 + 2, page.size());

        VideoMenuLayout.Placed reset = byKind(page, VideoMenuLayout.KIND_RESET);
        VideoMenuLayout.Placed done = byKind(page, VideoMenuLayout.KIND_DONE);
        assertEquals(W / 2 - 100, reset.x);
        assertEquals(200, reset.width);
        assertEquals(W / 2 - 100, done.x);
        assertEquals(200, done.width);
        assertTrue("reset sits above done", reset.y < done.y);
        // Two empty grid rows below the 5-row grid, Done 32 under Reset (reference).
        assertEquals(VideoMenuLayout.rowY(H, 7), reset.y);
        assertEquals(reset.y + 32, done.y);

        // Real backing: fullscreen + 3D anaglyph (vanilla), reset, done.
        assertEquals(2, kindCount(page, VideoMenuLayout.KIND_VANILLA));
    }

    @Test
    public void everyPageUsesTheReferenceGridAndUniqueIds()
    {
        for (int page = 0; page < VideoMenuLayout.PAGE_COUNT; ++page)
        {
            Set<Integer> ids = new HashSet<Integer>();

            for (VideoMenuLayout.Placed slot : VideoMenuLayout.layout(page, W, H))
            {
                assertTrue("duplicate id " + slot.id + " on page " + page,
                    ids.add(Integer.valueOf(slot.id)));

                if (slot.width == 150 && slot.kind != VideoMenuLayout.KIND_DONE)
                {
                    assertTrue("grid slots sit on the two reference columns",
                        slot.x == LEFT || slot.x == RIGHT);
                    assertEquals(20, slot.height);
                }
            }
        }

        assertEquals(W / 2 - 155, VideoMenuLayout.columnX(W, 0));
        assertEquals(W / 2 + 5, VideoMenuLayout.columnX(W, 1));
        assertEquals(H / 6 - 12, VideoMenuLayout.rowY(H, 0));
        assertEquals(H / 6 - 12 + 24, VideoMenuLayout.rowY(H, 1));
        // Grid-anchored bottom row with the degenerate-height clamp.
        assertEquals(VideoMenuLayout.rowY(H, 9) + 4, VideoMenuLayout.bottomRowY(H, 9));
        assertEquals(240 - 22, VideoMenuLayout.bottomRowY(240, 9));
    }

    private static void assertColumn(List<VideoMenuLayout.Placed> page, int x, int[] kinds)
    {
        List<VideoMenuLayout.Placed> column = new ArrayList<VideoMenuLayout.Placed>();

        for (VideoMenuLayout.Placed slot : page)
        {
            if (slot.x == x && slot.width == 150 && slot.kind != VideoMenuLayout.KIND_DONE)
            {
                column.add(slot);
            }
        }

        assertEquals(kinds.length, column.size());

        for (int i = 0; i < kinds.length; ++i)
        {
            assertEquals("column x=" + x + " row " + i, kinds[i], column.get(i).kind);
            assertEquals(VideoMenuLayout.rowY(H, i), column.get(i).y);
        }
    }

    private static VideoMenuLayout.Placed at(List<VideoMenuLayout.Placed> page, int x, int y)
    {
        for (VideoMenuLayout.Placed slot : page)
        {
            if (slot.x == x && slot.y == y)
            {
                return slot;
            }
        }

        throw new AssertionError("no slot at " + x + "," + y);
    }

    private static VideoMenuLayout.Placed byKind(List<VideoMenuLayout.Placed> page, int kind)
    {
        for (VideoMenuLayout.Placed slot : page)
        {
            if (slot.kind == kind)
            {
                return slot;
            }
        }

        throw new AssertionError("no slot of kind " + kind);
    }

    private static int kindCount(List<VideoMenuLayout.Placed> page, int kind)
    {
        int count = 0;

        for (VideoMenuLayout.Placed slot : page)
        {
            if (slot.kind == kind)
            {
                ++count;
            }
        }

        return count;
    }

    private static void assertDone(List<VideoMenuLayout.Placed> page, int x, int width)
    {
        VideoMenuLayout.Placed done = byKind(page, VideoMenuLayout.KIND_DONE);
        assertEquals(VideoMenuLayout.ID_DONE, done.id);
        assertEquals(x, done.x);
        assertEquals(width, done.width);
        assertEquals(VideoMenuLayout.bottomRowY(H, 9), done.y);
    }
}
