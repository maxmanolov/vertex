package vertex.benchmark;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HashingTest
{
    @Test
    public void calculatesSha256() throws Exception
    {
        Path file = Files.createTempFile("vertex-benchmark-hash", ".txt");

        try
        {
            Files.write(file, "abc".getBytes(StandardCharsets.UTF_8));
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Hashing.sha256(file));
        }
        finally
        {
            Files.deleteIfExists(file);
        }
    }
}
