package vertex.benchmark.plan;

import com.google.gson.annotations.SerializedName;

/** Defines the frame-data collector. */
public final class CollectorPlan
{
    public enum Type
    {
        @SerializedName("presentmon")
        PRESENTMON,
        @SerializedName("import")
        IMPORT
    }

    public enum Metric
    {
        @SerializedName("presented")
        PRESENTED,
        @SerializedName("displayed")
        DISPLAYED,
        @SerializedName("auto")
        AUTO
    }

    private Type type;
    private String executable;
    private Metric metric;

    public CollectorPlan()
    {
    }

    public Type getType()
    {
        return type;
    }

    public void setType(Type type)
    {
        this.type = type;
    }

    public String getExecutable()
    {
        return executable;
    }

    public void setExecutable(String executable)
    {
        this.executable = executable;
    }

    public Metric getMetric()
    {
        return metric;
    }

    public void setMetric(Metric metric)
    {
        this.metric = metric;
    }
}
