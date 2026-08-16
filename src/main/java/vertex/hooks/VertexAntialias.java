package vertex.hooks;

import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.PixelFormat;

/**
 * Restart-gated MSAA: the display-creation reroute asks for a multisampled context
 * when `antialiasing` is 2, 4 or 8. LWJGL types are plain classpath classes, so this
 * hook handles them directly - no reflection. The failure ladder preserves vanilla's:
 * a context that refuses the sampled format falls back to the vanilla format here,
 * and vanilla's own catch still guards the plain create() after that.
 */
public final class VertexAntialias
{
    /** Reroute of Display.create(PixelFormat) inside the display init. */
    public static void createDisplay(PixelFormat format) throws LWJGLException
    {
        int requested = samples(VertexConfig.value("antialiasing", "0"));

        if (requested > 0)
        {
            try
            {
                Display.create(format.withSamples(requested));
                LogWrapper.info("[Vertex] Antialiasing active: " + requested + "x requested, GL_SAMPLES="
                    + GL11.glGetInteger(GL13.GL_SAMPLES));
                return;
            }
            catch (LWJGLException e)
            {
                LogWrapper.warning("[Vertex] " + requested
                    + "x MSAA unavailable, using the vanilla format: " + e);
            }
        }

        Display.create(format);
    }

    /** antialiasing parses to 0/2/4/8; anything else is vanilla (0). */
    static int samples(String raw)
    {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.equals("2") ? 2 : trimmed.equals("4") ? 4 : trimmed.equals("8") ? 8 : 0;
    }

    private VertexAntialias()
    {
    }
}
