package vertex.benchmark;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ResultOpenerTest
{
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void opensAnExistingResult() throws Exception
    {
        Path result = temporaryFolder.newFile("summary.html").toPath();
        RecordingHandler handler = new RecordingHandler(true, false);

        assertTrue(new ResultOpener(handler).open(result, false));
        assertTrue(handler.opened.equals(result.toAbsolutePath().normalize()));
    }

    @Test
    public void noOpenDoesNotUseTheSystemHandler() throws Exception
    {
        Path result = temporaryFolder.newFile("summary.html").toPath();
        RecordingHandler handler = new RecordingHandler(true, false);

        assertFalse(new ResultOpener(handler).open(result, true));
        assertNull(handler.opened);
    }

    @Test
    public void returnsFalseWhenOpenIsNotAvailable() throws Exception
    {
        Path result = temporaryFolder.newFile("summary.html").toPath();

        assertFalse(new ResultOpener(new RecordingHandler(false, false))
            .open(result, false));
        assertFalse(new ResultOpener(new RecordingHandler(true, true))
            .open(result, false));
        assertFalse(new ResultOpener(new RecordingHandler(true, false))
            .open(result.resolveSibling("missing.html"), false));
    }

    @Test
    public void opensTheSuiteHtmlFile() throws Exception
    {
        Path output = temporaryFolder.newFolder("result").toPath();
        Path summary = output.resolve("summary.html");
        Files.write(summary, new byte[] { '<', 'h', '1', '>' });
        RecordingHandler handler = new RecordingHandler(true, false);

        assertTrue(new ResultOpener(handler).openSummary(output, false));
        assertTrue(handler.opened.equals(summary.toAbsolutePath().normalize()));
    }

    private static final class RecordingHandler implements ResultOpener.OpenHandler
    {
        private final boolean supported;
        private final boolean fail;
        private Path opened;

        private RecordingHandler(boolean supported, boolean fail)
        {
            this.supported = supported;
            this.fail = fail;
        }

        @Override
        public boolean isSupported()
        {
            return supported;
        }

        @Override
        public void open(Path path) throws IOException
        {
            if (fail)
            {
                throw new IOException("Test failure.");
            }
            opened = path;
        }
    }
}
