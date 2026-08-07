package vertex.benchmark;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import vertex.benchmark.report.SuiteReportWriter;

/** Opens a completed benchmark report with the system file handler. */
public final class ResultOpener
{
    /** Provides the system operation. Tests can supply a side-effect-free handler. */
    public interface OpenHandler
    {
        boolean isSupported();

        void open(Path path) throws IOException;
    }

    private final OpenHandler handler;

    public ResultOpener()
    {
        this(new DesktopOpenHandler());
    }

    public ResultOpener(OpenHandler handler)
    {
        if (handler == null)
        {
            throw new IllegalArgumentException("Open handler is required.");
        }
        this.handler = handler;
    }

    /** Opens summary.html unless the caller supplied --no-open. */
    public boolean openSummary(Path outputDirectory, boolean noOpen)
    {
        if (outputDirectory == null)
        {
            return false;
        }
        return open(outputDirectory.resolve(SuiteReportWriter.SUMMARY_HTML), noOpen);
    }

    /** Returns true only when the system accepted the open request. */
    public boolean open(Path path, boolean noOpen)
    {
        if (noOpen || path == null)
        {
            return false;
        }

        Path target = path.toAbsolutePath().normalize();
        try
        {
            if (!Files.exists(target) || !handler.isSupported())
            {
                return false;
            }
            handler.open(target);
            return true;
        }
        catch (IOException error)
        {
            return false;
        }
        catch (RuntimeException error)
        {
            return false;
        }
    }

    private static final class DesktopOpenHandler implements OpenHandler
    {
        @Override
        public boolean isSupported()
        {
            if (!Desktop.isDesktopSupported())
            {
                return false;
            }
            return Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
        }

        @Override
        public void open(Path path) throws IOException
        {
            Desktop.getDesktop().open(path.toFile());
        }
    }
}
