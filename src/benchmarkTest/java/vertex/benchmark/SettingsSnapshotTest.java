package vertex.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsSnapshotTest
{
    @Test
    public void recordsAnInvalidPathAsASnapshotFailure() throws Exception
    {
        Path directory = Files.createTempDirectory("vertex-settings-snapshot");
        List<String> warnings = new ArrayList<String>();
        Map<String, String> snapshot = SettingsSnapshot.capture(
            Collections.singletonList("bad\0path"), directory, warnings);

        assertTrue(SettingsSnapshot.hasFailures(snapshot));
        assertFalse(warnings.isEmpty());
    }
}
