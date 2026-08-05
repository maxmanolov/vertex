package vertex.transform;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Dispatches per-class bytecode patches. Vertex targets the obfuscated (notch) names of the
 * vanilla 1.7.10 client only; in any other environment the names will not match and every
 * class passes through untouched.
 */
public class VertexTransformer implements IClassTransformer
{
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass)
    {
        if (basicClass == null)
        {
            return null;
        }

        try
        {
            if (Mappings.WORLD_RENDERER.equals(name))
            {
                LogWrapper.info("[Vertex] Patching WorldRenderer (" + name + ")");
                return WorldRendererPatch.apply(basicClass);
            }

            if (Mappings.RENDER_GLOBAL.equals(name))
            {
                LogWrapper.info("[Vertex] Patching RenderGlobal (" + name + ")");
                return RenderGlobalPatch.apply(basicClass);
            }
        }
        catch (Exception e)
        {
            // A failed patch must never take the game down; fall back to vanilla bytes.
            LogWrapper.severe("[Vertex] Failed to patch " + name + ", leaving class unmodified");
            e.printStackTrace();
        }

        return basicClass;
    }
}
