package vertex.render;

/**
 * Pure lifecycle and motion model for the short-lived cloud display-list cache. The
 * render hook owns the OpenGL id; this class owns only the facts that decide whether
 * its contents still describe the current scene.
 */
public final class CloudCacheState
{
    public static final int REBUILD_INTERVAL_TICKS = 20;
    /** Vanilla multiplies by the float literal 0.03F widened to double; match it bit-exact. */
    public static final double CLOUD_DRIFT_PER_TICK = (double)0.03F;

    private boolean valid;
    private Object owner;
    private Object world;
    private int mode;
    private int tick;
    private double cloudTime;
    private double cameraX;
    private double cameraY;
    private double cameraZ;

    public void capture(Object owner, Object world, int mode, int tick, float partialTick,
        double cameraX, double cameraY, double cameraZ)
    {
        this.valid = true;
        this.owner = owner;
        this.world = world;
        this.mode = mode;
        this.tick = tick;
        this.cloudTime = tick + (double)partialTick;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
    }

    public boolean reusable(Object owner, Object world, int mode, int tick)
    {
        int age = tick - this.tick;
        return this.valid && this.owner == owner && this.world == world && this.mode == mode
            && age >= 0 && age < REBUILD_INTERVAL_TICKS;
    }

    public double deltaX(int tick, float partialTick, double currentCameraX)
    {
        return currentCameraX - this.cameraX
            + ((tick + (double)partialTick) - this.cloudTime) * CLOUD_DRIFT_PER_TICK;
    }

    public double deltaY(double currentCameraY)
    {
        return currentCameraY - this.cameraY;
    }

    public double deltaZ(double currentCameraZ)
    {
        return currentCameraZ - this.cameraZ;
    }

    public void clear()
    {
        this.valid = false;
        this.owner = null;
        this.world = null;
    }
}
