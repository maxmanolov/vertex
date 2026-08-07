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
 * are clean defaults; a malformed reload publishes one empty state. Listing-based
 * features (arbitrary CTM file names) need pack-zip walking and land separately.
 */
public final class VertexPackLoader
{
    static final class PackState
    {
        final ColorProperties colorProperties;
        final ColorMap grassMap;
        final ColorMap foliageMap;
        final vertex.variants.NaturalProperties naturalProperties;
        final java.util.List<vertex.sky.SkyLayer> skyLayers;

        PackState(ColorProperties colorProperties, ColorMap grassMap, ColorMap foliageMap,
            vertex.variants.NaturalProperties naturalProperties,
            java.util.List<vertex.sky.SkyLayer> skyLayers)
        {
            this.colorProperties = colorProperties;
            this.grassMap = grassMap;
            this.foliageMap = foliageMap;
            this.naturalProperties = naturalProperties;
            this.skyLayers = skyLayers;
        }
    }

    private static final PackState EMPTY = new PackState(null, null, null, null,
        java.util.Collections.<vertex.sky.SkyLayer>emptyList());
    private static volatile PackState state = EMPTY;

    private static boolean registered = false;
    private static boolean disabled = false;
    private static Method getResource;
    private static Object lastManager;
    private static Method getStream;
    private static Constructor<?> locationCtor;

    static ColorProperties colorProperties()
    {
        return state.colorProperties;
    }

    static ColorMap grassMap()
    {
        return state.grassMap;
    }

    static ColorMap foliageMap()
    {
        return state.foliageMap;
    }

    static vertex.variants.NaturalProperties naturalProperties()
    {
        return state.naturalProperties;
    }

    static java.util.List<vertex.sky.SkyLayer> skyLayers()
    {
        return state.skyLayers;
    }

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
        lastManager = manager;
        VertexRandomEntities.onResourceReload();
        VertexNaturalIcons.onResourceReload();

        try
        {
            Properties colorProps = readProperties(manager, "mcpatcher/color.properties");
            ColorProperties nextColorProperties = colorProps != null ? new ColorProperties(colorProps) : null;
            ColorMap nextGrassMap = readColorMap(manager, "mcpatcher/colormap/grass.png");
            ColorMap nextFoliageMap = readColorMap(manager, "mcpatcher/colormap/foliage.png");
            // Sky layers are numbered by convention, so fixed-path probing suffices:
            // probe skyN.properties until a gap, capped.
            java.util.List<vertex.sky.SkyLayer> layers = new java.util.ArrayList<vertex.sky.SkyLayer>();

            for (int index = 1; index <= 16; ++index)
            {
                String base = "mcpatcher/sky/world0/sky" + index;
                Properties layerProps = readProperties(manager, base + ".properties");

                if (layerProps == null)
                {
                    break;
                }

                try
                {
                    vertex.sky.SkyLayer layer = vertex.sky.SkyLayer.parse(layerProps, base + ".png");

                    if (layer != null)
                    {
                        layers.add(layer);
                    }
                }
                catch (Exception malformed)
                {
                    LogWrapper.warning("[Vertex] Skipping sky layer " + index + ": " + malformed);
                }
            }

            Properties naturalProps = readProperties(manager, "mcpatcher/natural.properties");
            vertex.variants.NaturalProperties nextNaturalProperties = naturalProps != null
                ? new vertex.variants.NaturalProperties(naturalProps) : null;
            PackState next = new PackState(nextColorProperties, nextGrassMap, nextFoliageMap,
                nextNaturalProperties,
                java.util.Collections.unmodifiableList(new java.util.ArrayList<vertex.sky.SkyLayer>(layers)));
            // One write publishes the complete reload to client and worker threads (#103).
            state = next;

            if (next.naturalProperties != null)
            {
                VertexIcons.activate();
            }
            LogWrapper.info("[Vertex] Pack resources reloaded (colors: "
                + (next.colorProperties != null ? next.colorProperties.size() + " keys" : "none")
                + ", colormaps: " + (next.grassMap != null ? "grass " : "") + (next.foliageMap != null ? "foliage" : "")
                + ", natural: " + (next.naturalProperties != null ? next.naturalProperties.size() + " tiles" : "none")
                + ", sky layers: " + next.skyLayers.size() + ")");
        }
        catch (Exception e)
        {
            state = EMPTY;
            LogWrapper.warning("[Vertex] Pack reload failed, using defaults: " + e);
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

    /** True when the given ResourceLocation resolves in the active packs. */
    public static boolean resourceExists(Object location)
    {
        try
        {
            Object resource = getResource.invoke(lastManager, location);
            return resource != null;
        }
        catch (Exception absent)
        {
            return false;
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
