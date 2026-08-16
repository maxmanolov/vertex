package vertex.hooks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.lwjgl.opengl.DisplayMode;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Fullscreen Mode decision logic: parsing, mode choice, label list, cycling. */
public final class VertexFullscreenTest
{
    @Test
    public void parseAcceptsOnlyRealResolutions()
    {
        assertArrayEquals(new int[] {1920, 1080}, VertexFullscreen.parseMode("1920x1080"));
        assertArrayEquals(new int[] {800, 600}, VertexFullscreen.parseMode(" 800x600 "));
        assertNull(VertexFullscreen.parseMode("default"));
        assertNull(VertexFullscreen.parseMode(null));
        assertNull(VertexFullscreen.parseMode(""));
        assertNull(VertexFullscreen.parseMode("1920x"));
        assertNull(VertexFullscreen.parseMode("x1080"));
        assertNull(VertexFullscreen.parseMode("1920x1080x32"));
        assertNull(VertexFullscreen.parseMode("axb"));
        assertNull(VertexFullscreen.parseMode("0x600"));
        assertNull(VertexFullscreen.parseMode("800x-600"));
    }

    @Test
    public void chooseIndexPrefersDepthThenFrequency()
    {
        int[][] rows = {
            {1920, 1080, 16, 60},
            {1920, 1080, 32, 60},
            {1920, 1080, 32, 144},
            {2560, 1440, 32, 60},
        };

        // Highest bpp wins, then highest frequency among equals.
        assertEquals(2, VertexFullscreen.chooseIndex(rows, 1920, 1080));
        assertEquals(3, VertexFullscreen.chooseIndex(rows, 2560, 1440));
        assertEquals(-1, VertexFullscreen.chooseIndex(rows, 1280, 720));
        assertEquals(-1, VertexFullscreen.chooseIndex(new int[0][], 1920, 1080));
    }

    @Test
    public void labelsDedupeAndSortByAreaThenWidth()
    {
        DisplayMode[] modes = {
            new DisplayMode(1920, 1080),
            new DisplayMode(800, 600),
            new DisplayMode(1920, 1080), // bpp/frequency variant collapses
            new DisplayMode(1280, 720),
            new DisplayMode(1080, 1920), // same area as 1920x1080, narrower first
        };

        assertEquals(Arrays.asList("800x600", "1280x720", "1080x1920", "1920x1080"),
            VertexFullscreen.labelsFrom(modes));
    }

    @Test
    public void theCycleWalksDefaultThroughEveryModeAndWraps()
    {
        List<String> labels = Arrays.asList("800x600", "1920x1080");

        assertEquals("800x600", VertexFullscreen.nextIn(labels, "default"));
        assertEquals("1920x1080", VertexFullscreen.nextIn(labels, "800x600"));
        assertEquals("default", VertexFullscreen.nextIn(labels, "1920x1080"));
        // A stale stored value (monitor changed) restarts at default.
        assertEquals("default", VertexFullscreen.nextIn(labels, "2560x1440"));
        assertEquals("default", VertexFullscreen.nextIn(Collections.<String>emptyList(), "default"));
    }

    @Test
    public void theMenuLabelShowsDefaultOrTheStoredMode()
    {
        assertEquals("Fullscreen Mode: Default", VideoMenuLayout.fullscreenModeLabel("default"));
        assertEquals("Fullscreen Mode: Default", VideoMenuLayout.fullscreenModeLabel(null));
        assertEquals("Fullscreen Mode: 1920x1080", VideoMenuLayout.fullscreenModeLabel("1920x1080"));
    }
}
