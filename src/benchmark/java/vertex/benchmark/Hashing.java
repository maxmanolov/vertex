package vertex.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Calculates stable file digests for a run record. */
public final class Hashing
{
    public static String sha256(Path file) throws IOException
    {
        MessageDigest digest;

        try
        {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException unavailable)
        {
            throw new IllegalStateException("SHA-256 is not available.", unavailable);
        }

        InputStream input = Files.newInputStream(file);

        try
        {
            byte[] buffer = new byte[65536];
            int count;

            while ((count = input.read(buffer)) >= 0)
            {
                if (count > 0)
                {
                    digest.update(buffer, 0, count);
                }
            }
        }
        finally
        {
            input.close();
        }

        StringBuilder value = new StringBuilder(64);

        for (byte item : digest.digest())
        {
            value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        }

        return value.toString();
    }

    private Hashing()
    {
    }
}
