package vertex.hooks;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import vertex.Mappings;

/**
 * Mirror-variant icon proxies for natural textures. A proxy delegates every IIcon call to
 * the base sprite except the U/V bounds, which are swapped per axis to mirror the tile.
 * Proxies are created once per (sprite, variant) and cached, so the render path only does
 * a map lookup. Self-disables on any failure.
 */
public final class VertexNaturalIcons
{
    // ConcurrentHashMap: multicore workers reach this cache in parallel through the icon
    // dispatch during chunk builds (kyrofx #36). Racing get/put may at worst create a
    // duplicate proxy, which is benign; a torn HashMap is not.
    private static final Map<String, Object> cache = new ConcurrentHashMap<String, Object>();
    private static volatile boolean broken = false;

    public static Object variant(final Object baseIcon, final boolean flipU, final boolean flipV)
    {
        if (broken || (!flipU && !flipV))
        {
            return baseIcon;
        }

        try
        {
            String key = System.identityHashCode(baseIcon) + ":" + (flipU ? 1 : 0) + (flipV ? 1 : 0);
            Object cached = cache.get(key);

            if (cached != null)
            {
                return cached;
            }

            Class<?> iconClass = Class.forName(Mappings.IICON, false, baseIcon.getClass().getClassLoader());
            Object proxy = Proxy.newProxyInstance(baseIcon.getClass().getClassLoader(), new Class<?>[] {iconClass},
                new InvocationHandler()
                {
                    public Object invoke(Object self, Method method, Object[] args) throws Throwable
                    {
                        String name = method.getName();
                        int argCount = args == null ? 0 : args.length;

                        if (argCount == 0)
                        {
                            if (flipU && name.equals(Mappings.ICON_MIN_U))
                            {
                                return call(baseIcon, Mappings.ICON_MAX_U);
                            }

                            if (flipU && name.equals(Mappings.ICON_MAX_U))
                            {
                                return call(baseIcon, Mappings.ICON_MIN_U);
                            }

                            if (flipV && name.equals(Mappings.ICON_MIN_V))
                            {
                                return call(baseIcon, Mappings.ICON_MAX_V);
                            }

                            if (flipV && name.equals(Mappings.ICON_MAX_V))
                            {
                                return call(baseIcon, Mappings.ICON_MIN_V);
                            }
                        }
                        else if (argCount == 1 && args[0] instanceof Double)
                        {
                            double value = ((Double)args[0]).doubleValue();
                            boolean mirrored = name.equals(Mappings.ICON_INTERP_U) ? flipU : flipV;
                            return method.invoke(baseIcon, Double.valueOf(mirrored ? 16.0D - value : value));
                        }

                        return method.invoke(baseIcon, args);
                    }
                });
            cache.put(key, proxy);
            return proxy;
        }
        catch (Throwable e)
        {
            broken = true;
            net.minecraft.launchwrapper.LogWrapper.severe("[Vertex] Natural texture proxies disabled after failure");
            e.printStackTrace();
            return baseIcon;
        }
    }

    private static Object call(Object icon, String method) throws Exception
    {
        Method target = icon.getClass().getMethod(method);
        target.setAccessible(true);
        return target.invoke(icon);
    }

    private VertexNaturalIcons()
    {
    }
}
