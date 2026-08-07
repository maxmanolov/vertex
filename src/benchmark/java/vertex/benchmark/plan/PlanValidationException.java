package vertex.benchmark.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reports all detected plan errors. */
public final class PlanValidationException extends IllegalArgumentException
{
    private final List<String> violations;

    public PlanValidationException(String violation)
    {
        this(Collections.singletonList(violation));
    }

    public PlanValidationException(List<String> violations)
    {
        super(join(violations));
        this.violations = Collections.unmodifiableList(new ArrayList<String>(violations));
    }

    public List<String> getViolations()
    {
        return violations;
    }

    private static String join(List<String> values)
    {
        StringBuilder message = new StringBuilder("Invalid benchmark plan: ");
        for (int index = 0; index < values.size(); index++)
        {
            if (index > 0)
            {
                message.append("; ");
            }
            message.append(values.get(index));
        }
        return message.toString();
    }
}
