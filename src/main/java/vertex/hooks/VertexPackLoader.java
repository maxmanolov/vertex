package vertex.hooks;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;
import vertex.colors.ColorMap;
import vertex.colors.ColorProperties;

/**
 * Shared resource-pack loading for the visual features. Registers a reload listener at
 * runtime by proxying the obfuscated listener interface (no bytecode involved), then on
 * every resource reload probes the documented fixed paths - custom colors first:
 * mcpatcher/color.properties and mcpatcher/colormap/{grass,foliage}.png. Absent resources
 * are clean defaults; a malformed resource logs and is skipped. Listing-based features
 * (arbitrary CTM file names) need pack-zip walking and land separately.
 */
public final class VertexPackLoader
{
    public static volatile ColorProperties colorProperties;
    public static volatile ColorMap grassMap;
    public static volatile ColorMap foliageMap;
    public static volatile vertex.variants.NaturalProperties naturalProperties;

    private static boolean registered = false;
    private static boolean disabled = false;
    private static Method getResource;
    private static Method getStream;
    private static Constructor<?> locationCtor;

    public static void tick(Object minecraft)
    {
        if (registered || disabled)
        {
            return;
        }

        try
        {
            Field managerField = minecraft.getClass().getDeclaredField(Mappings.MC_RESOURCE_MANAGER);
            managerField.setAccessible(true);
            final Object manager = managerField.get(minecraft);

            if (manager == null)
            {
                return;
            }

            ClassLoader gameLoader = minecraft.getClass().getClassLoader();
            Class<?> listenerIface = Class.forName(Mappings.RELOAD_LISTENER_IFACE, false, gameLoader);
            Class<?> locationClass = Class.forName(Mappings.RESOURCE_LOCATION, false, gameLoader);
            locationCtor = locationClass.getConstructor(String.class);

            for (Method method : manager.getClass().getMethods())
            {
                if (method.getName().equals(Mappings.GET_RESOURCE) && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0] == locationClass)
                {
                    getResource = method;
                }
            }

            Object listener = Proxy.newProxyInstance(gameLoader, new Class<?>[] {listenerIface}, new InvocationHandler()
            {
                public Object invoke(Object proxy, Method method, Object[] args)
                {
                    reload(manager);
                    return null;
                }
            });
            Method register = manager.getClass().getMethod(Mappings.REGISTER_RELOAD_LISTENER, listenerIface);
            register.invoke(manager, listener);
            registered = true;
        }
        catch (Exception e)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Pack loader disabled after failure");
            e.printStackTrace();
        }
    }

    static void reload(Object manager)
    {
        try
        {
            Properties colorProps = readProperties(manager, "mcpatcher/color.properties");
            colorProperties = colorProps != null ? new ColorProperties(colorProps) : null;
            grassMap = readColorMap(manager, "mcpatcher/colormap/grass.png");
            foliageMap = readColorMap(manager, "mcpatcher/colormap/foliage.png");
            Properties naturalProps = readProperties(manager, "mcpatcher/natural.properties");
            naturalProperties = naturalProps != null ? new vertex.variants.NaturalProperties(naturalProps) : null;

            if (naturalProperties != null)
            {
                VertexIcons.activate();
            }
            LogWrapper.info("[Vertex] Pack resources reloaded (colors: "
                + (colorProperties != null ? colorProperties.size() + " keys" : "none")
                + ", colormaps: " + (grassMap != null ? "grass " : "") + (foliageMap != null ? "foliage" : "")
                + ", natural: " + (naturalProperties != null ? naturalProperties.size() + " tiles" : "none") + ")");
        }
        catch (Exception e)
        {
            LogWrapper.warning("[Vertex] Pack reload failed, keeping defaults: " + e);
        }
    }

    private static Properties readProperties(Object manager, String path) throws Exception
    {
        InputStream in = open(manager, path);

        if (in == null)
        {
            return null;
        }

        try
        {
            Properties props = new Properties();
            props.load(in);
            return props;
        }
        finally
        {
            in.close();
        }
    }

    private static ColorMap readColorMap(Object manager, String path) throws Exception
    {
        InputStream in = open(manager, path);

        if (in == null)
        {
            return null;
        }

        try
        {
            BufferedImage image = ImageIO.read(in);

            if (image == null || image.getWidth() != ColorMap.SIZE || image.getHeight() != ColorMap.SIZE)
            {
                LogWrapper.warning("[Vertex] Ignoring colormap with wrong dimensions: " + path);
                return null;
            }

            int[] pixels = new int[ColorMap.SIZE * ColorMap.SIZE];
            image.getRGB(0, 0, ColorMap.SIZE, ColorMap.SIZE, pixels, 0, ColorMap.SIZE);
            return new ColorMap(pixels);
        }
        finally
        {
            in.close();
        }
    }

    private static InputStream open(Object manager, String path)
    {
        try
        {
            Object location = locationCtor.newInstance(path);
            Object resource = getResource.invoke(manager, location);
            Method stream = resource.getClass().getMethod(Mappings.RESOURCE_GET_STREAM);
            stream.setAccessible(true);
            return (InputStream)stream.invoke(resource);
        }
        catch (Exception absentOrUnreadable)
        {
            return null;
        }
    }

    private VertexPackLoader()
    {
    }
}
