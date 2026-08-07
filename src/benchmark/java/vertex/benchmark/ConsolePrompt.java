package vertex.benchmark;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/** Handles the manual steps in a local run. */
public class ConsolePrompt
{
    private final BufferedReader input;

    public ConsolePrompt()
    {
        Console console = System.console();
        input = console == null
            ? new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()))
            : new BufferedReader(console.reader());
    }

    public void waitForEnter(String message) throws IOException
    {
        System.out.println(message);
        String line = input.readLine();

        if (line == null)
        {
            throw new IOException("The benchmark needs interactive input.");
        }
    }

    public String readRequired(String message) throws IOException
    {
        while (true)
        {
            System.out.println(message);
            String line = input.readLine();

            if (line == null)
            {
                throw new IOException("The benchmark needs interactive input.");
            }

            if (!line.trim().isEmpty())
            {
                return line.trim();
            }
        }
    }

    public void waitSeconds(int seconds) throws InterruptedException
    {
        waitSeconds("Warm-up", seconds);
    }

    public void waitSeconds(String phase, int seconds) throws InterruptedException
    {
        if (seconds <= 0)
        {
            return;
        }

        System.out.println(phase + ": " + seconds + " seconds.");

        for (int remaining = seconds; remaining > 0; --remaining)
        {
            if (remaining == seconds || remaining <= 5 || remaining % 10 == 0)
            {
                System.out.println(phase + " remaining: " + remaining + " seconds.");
            }

            Thread.sleep(1000L);
        }
    }
}
