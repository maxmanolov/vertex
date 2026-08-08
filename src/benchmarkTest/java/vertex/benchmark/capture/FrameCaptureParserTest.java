package vertex.benchmark.capture;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FrameCaptureParserTest
{
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void parsesV1BomCrlfQuotedFieldsAndExtraColumns() throws Exception
    {
        String csv = "\uFEFFApplication,ProcessID,SwapChainAddress,Dropped,TimeInSeconds,"
            + "MsBetweenPresents,MsBetweenDisplayChange,Extra\r\n"
            + "\"Minecraft, Java\",42,0xA,0,1.250,16.0,17.0,\"a,b\"\r\n"
            + "\"Minecraft, Java\",42,0xA,1,1.270,20.0,NA,unused\r\n"
            + "Other.exe,43,0xB,0,1.290,18.0,19.0,unused\r\n";
        File file = temporaryFolder.newFile("presentmon-v1.csv");
        Files.write(file.toPath(), csv.getBytes(StandardCharsets.UTF_8));

        FrameCapture capture = new FrameCaptureParser().parse(file.toPath(),
            FrameTimePreference.PRESENTED);

        assertEquals("MsBetweenPresents", capture.getFrameTimeSource());
        assertEquals(3, capture.getSamples().size());
        assertEquals(2, capture.getSeries().size());
        FrameSample first = capture.getSamples().get(0);
        assertEquals("Minecraft, Java", first.getProcessName());
        assertEquals(42L, first.getProcessId());
        assertEquals("0xA", first.getSwapChain());
        assertEquals("1.250", first.getTimestamps().get("TimeInSeconds"));
        assertFalse(first.isDropped());
        assertTrue(capture.getSamples().get(1).isDropped());

        FrameSeriesSelection selection = capture.selectLargestSeries();
        assertTrue(selection.isPresent());
        assertEquals(new FrameSeriesKey(42L, "0xA"), selection.getKey());
        assertEquals(2, selection.getSamples().size());
        assertFalse(selection.getWarnings().isEmpty());
    }

    @Test
    public void selectsV2DisplayedAndPresentedMetrics() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,CPUStartDateTime,"
            + "FrameTime,DisplayedTime,MsBetweenAppStart\n"
            + "javaw.exe,7,0x1,2026-08-07T01:02:03.123456789,10.0,12.5,11.0\n";
        FrameCaptureParser parser = new FrameCaptureParser();

        FrameCapture automatic = parser.parse(new StringReader(csv), FrameTimePreference.AUTO);
        FrameCapture presented = parser.parse(new StringReader(csv),
            FrameTimePreference.PRESENTED);
        FrameCapture displayed = parser.parse(new StringReader(csv),
            FrameTimePreference.DISPLAYED);

        assertEquals("FrameTime", automatic.getFrameTimeSource());
        assertEquals(10.0D, automatic.getSamples().get(0).getFrameTimeMillis(), 0.0D);
        assertEquals("FrameTime", presented.getFrameTimeSource());
        assertEquals(10.0D, presented.getSamples().get(0).getFrameTimeMillis(), 0.0D);
        assertEquals("DisplayedTime", displayed.getFrameTimeSource());
        assertEquals("2026-08-07T01:02:03.123456789",
            displayed.getSamples().get(0).getTimestamps().get("CPUStartDateTime"));
    }

    @Test
    public void supportsLegacyFallbackMetricNames() throws Exception
    {
        assertSource("MsBetweenAppStart", FrameTimePreference.PRESENTED);
        assertSource("MsBetweenDisplayChange", FrameTimePreference.DISPLAYED);
        assertSource("FrameTime", FrameTimePreference.PRESENTED);
    }

    @Test
    public void rejectsInvalidFrameTimesAndReportsRows() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,FrameTime\n"
            + "javaw.exe,7,0x1,NaN\n"
            + "javaw.exe,7,0x1,Infinity\n"
            + "javaw.exe,7,0x1,0\n"
            + "javaw.exe,7,0x1,-1\n"
            + "javaw.exe,7,0x1,text\n"
            + "javaw.exe,7,0x1,8.25\n";

        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.PRESENTED);

        assertEquals(5, capture.getInvalidRowCount());
        assertEquals(1, capture.getSamples().size());
        assertEquals(8.25D, capture.getSamples().get(0).getFrameTimeMillis(), 0.0D);
        assertTrue(capture.getWarnings().get(0).contains("CSV row"));
    }

    @Test
    public void countsDisplayedNaAsDroppedWithoutAnInvalidRow() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,DisplayedTime\n"
            + "javaw.exe,7,0x1,NA\n"
            + "javaw.exe,7,0x1,12.0\n";

        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.DISPLAYED);

        assertEquals(1, capture.getDroppedRowCount());
        assertEquals(0, capture.getInvalidRowCount());
        assertEquals(1, capture.getSamples().size());
    }

    @Test
    public void autoFallsBackWhenPresentedValuesAreUnavailable() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,FrameTime,DisplayedTime\n"
            + "javaw.exe,7,0x1,NA,12.0\n";

        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.AUTO);

        assertEquals("DisplayedTime", capture.getFrameTimeSource());
        assertEquals(12.0D, capture.getSamples().get(0).getFrameTimeMillis(), 0.0D);
    }

    @Test
    public void autoUsesWholeCaptureColumnPriority() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,FrameTime,DisplayedTime\n"
            + "javaw.exe,7,0x1,NA,12.0\n"
            + "javaw.exe,7,0x1,10.0,12.0\n";

        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.AUTO);

        assertEquals("FrameTime", capture.getFrameTimeSource());
        assertEquals(1, capture.getSamples().size());
        assertEquals(10.0D, capture.getSamples().get(0).getFrameTimeMillis(), 0.0D);
    }

    @Test
    public void rejectsNegativeProcessIds() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,FrameTime\n"
            + "javaw.exe,-7,0x1,10.0\n";

        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.PRESENTED);

        assertEquals(1, capture.getInvalidRowCount());
        assertTrue(capture.getSamples().isEmpty());
    }

    @Test
    public void reportsAnEqualLargestSeriesSelection() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,MsBetweenPresents\n"
            + "a.exe,1,0x1,10\n"
            + "b.exe,2,0x2,11\n";
        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.PRESENTED);
        FrameSeriesSelection selection = capture.selectLargestSeries();

        assertEquals(new FrameSeriesKey(1L, "0x1"), selection.getKey());
        assertEquals(2, selection.getWarnings().size());
        assertTrue(selection.getWarnings().get(1).contains("2 frame series have 1"));
    }

    @Test
    public void selectsOneExplicitProcessBeforeItSelectsASwapChain() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,MsBetweenPresents\n"
            + "old.exe,1,0x1,10\n"
            + "old.exe,1,0x1,10\n"
            + "game.exe,2,0x2,11\n";
        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.PRESENTED);
        FrameSeriesSelection selection = capture.selectLargestSeriesForProcess(2L);

        assertTrue(selection.isPresent());
        assertEquals(new FrameSeriesKey(2L, "0x2"), selection.getKey());
        assertEquals(1, selection.getSamples().size());
    }

    @Test
    public void selectsOneExactSwapChain() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,MsBetweenPresents\n"
            + "game.exe,2,0x1,10\n"
            + "game.exe,2,0x2,11\n";
        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.PRESENTED);
        FrameSeriesSelection selection = capture.selectSeries(
            new FrameSeriesKey(2L, "0x2"));

        assertTrue(selection.isPresent());
        assertEquals(new FrameSeriesKey(2L, "0x2"), selection.getKey());
        assertEquals(11.0D, selection.getSamples().get(0).getFrameTimeMillis(), 0.0D);
    }

    @Test
    public void returnsAnEmptySelectionForAnEmptyCapture() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,FrameTime\n";
        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.PRESENTED);

        assertFalse(capture.selectLargestSeries().isPresent());
        assertFalse(capture.selectLargestSeries().getWarnings().isEmpty());
    }

    @Test
    public void usesOneDefaultSeriesForGenericFrameData() throws Exception
    {
        String csv = "timestamp_ms,FrameTime\n100,10.0\n110,12.0\n";
        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.PRESENTED);

        assertEquals(2, capture.getSamples().size());
        assertEquals(new FrameSeriesKey(0L, ""), capture.selectLargestSeries().getKey());
        assertTrue(capture.getWarnings().toString(), capture.getWarnings().get(0)
            .contains("Process ID 0"));
    }

    @Test
    public void exposesReadOnlySamplesAndSeries() throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress,FrameTime\n"
            + "javaw.exe,7,0x1,10\n";
        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            FrameTimePreference.PRESENTED);
        Map<FrameSeriesKey, List<FrameSample>> series = capture.getSeries();

        assertEquals(1, series.size());

        try
        {
            capture.getSamples().clear();
        }
        catch (UnsupportedOperationException expected)
        {
            return;
        }

        throw new AssertionError("Samples must be read-only.");
    }

    private static void assertSource(String source, FrameTimePreference preference)
        throws Exception
    {
        String csv = "Application,ProcessID,SwapChainAddress," + source + "\n"
            + "javaw.exe,7,0x1,9.5\n";
        FrameCapture capture = new FrameCaptureParser().parse(new StringReader(csv),
            preference);
        assertEquals(source, capture.getFrameTimeSource());
        assertEquals(9.5D, capture.getSamples().get(0).getFrameTimeMillis(), 0.0D);
    }
}
