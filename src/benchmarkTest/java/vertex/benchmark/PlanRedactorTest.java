package vertex.benchmark;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlanRedactorTest
{
    @Test
    public void doesNotStoreCommandArguments()
    {
        List<String> result = PlanRedactor.redactCommand(Arrays.asList(
            "java", "--accessToken", "private-value", "--api-key=other-value",
            "--clientToken", "third-value", "--username", "Player"));

        assertEquals(Arrays.asList("<not stored>"), result);
    }
}
