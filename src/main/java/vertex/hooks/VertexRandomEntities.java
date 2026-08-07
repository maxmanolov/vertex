package vertex.hooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;
import vertex.variants.DeterministicVariants;

/**
 * Random entity textures: an entity whose texture has numbered siblings (cow2.png,
 * cow3.png, ...) renders a variant chosen deterministically from its entity id, so a
 * given mob keeps its appearance for its whole life and across rebuilds. Variant counts
 * are discovered once per base texture by probing the resource manager and cached; the
 * render path then costs a map lookup and an integer hash.
 *
 * Guards the whole feature behind config and self-disables on any failure.
 */
public final class VertexRandomEntities
{
    private static final Map<String, Object[]> variants = new HashMap<String, Object[]>();
    private static boolean disabled = false;
    private static Method getEntityTexture;
    private static Method bindTexture;
    private static Method getResourcePath;
    private static Method getResourceDomain;
    private static Method getEntityId;
    private static Constructor<?> locationCtor;

    public static long applied = 0L;

    /** Resource reload: variant pools describe the OLD pack's files; rediscover fresh. */
    public static void onResourceReload()
    {
        variants.clear();
    }

    /** Head guard on Render.bindEntityTexture: true = variant bound, skip vanilla. */
    public static boolean interceptBind(Object render, Object entity)
    {
        if (disabled || entity == null || !VertexConfig.enabled("randomEntities"))
        {
            return false;
        }

        try
        {
            if (getEntityTexture == null)
            {
                initialize(render, entity);
            }

            Object base = getEntityTexture.invoke(render, entity);

            if (base == null)
            {
                return false;
            }

            String path = (String)getResourcePath.invoke(base);
            String domain = (String)getResourceDomain.invoke(base);
            String key = domain + ":" + path;
            Object[] pool = variants.get(key);

            if (pool == null)
            {
                pool = discover(base, path);
                variants.put(key, pool);
            }

            if (pool.length <= 1)
            {
                return false;
            }

            int id = ((Integer)getEntityId.invoke(entity)).intValue();
            Object chosen = pool[DeterministicVariants.pick(DeterministicVariants.hash(id, 0, 0, 31), pool.length)];

            if (chosen == base)
            {
                return false;
            }

            bindTexture.invoke(render, chosen);
            ++applied;
            return true;
        }
        catch (Throwable e)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Random entities disabled after failure");
            e.printStackTrace();
            return false;
        }
    }

    /** Probes base.png, base2.png, base3.png ... until one is missing (capped). */
    private static Object[] discover(Object base, String path) throws Exception
    {
        java.util.List<Object> found = new java.util.ArrayList<Object>();
        found.add(base);

        if (path.endsWith(".png"))
        {
            String stem = path.substring(0, path.length() - 4);
            String domain = (String)getResourceDomain.invoke(base);

            for (int index = 2; index <= 16; ++index)
            {
                Object candidate = locationCtor.newInstance(domain, stem + index + ".png");

                if (!VertexPackLoader.resourceExists(candidate))
                {
                    break;
                }

                found.add(candidate);
            }
        }

        if (found.size() > 1)
        {
            LogWrapper.info("[Vertex] Random entities: " + found.size() + " variants for " + path);
        }

        return found.toArray();
    }

    private static void initialize(Object render, Object entity) throws Exception
    {
        // Resolve handles on the Render BASE class, not the concrete subclass: a Method
        // taken from RenderCow cannot be invoked on a RenderSheep, but the base-class
        // handle dispatches virtually to whichever override the instance has.
        Class<?> renderClass = render.getClass();

        while (renderClass.getSuperclass() != null && renderClass.getSuperclass() != Object.class)
        {
            renderClass = renderClass.getSuperclass();
        }

        Class<?> entityRoot = entity.getClass();

        while (entityRoot.getSuperclass() != Object.class)
        {
            entityRoot = entityRoot.getSuperclass();
        }

        // getEntityTexture and bindTexture are protected in Render, so getMethods() (public
        // only) never sees them; declared methods must be collected up the hierarchy.
        getEntityTexture = findDeclared(renderClass, Mappings.RENDER_GET_ENTITY_TEXTURE, entityRoot, null);

        if (getEntityTexture == null)
        {
            throw new IllegalStateException("getEntityTexture not resolved on " + renderClass.getName());
        }

        Class<?> locationClass = getEntityTexture.getReturnType();
        locationCtor = locationClass.getConstructor(String.class, String.class);
        getResourcePath = locationClass.getMethod(Mappings.LOCATION_PATH);
        getResourceDomain = locationClass.getMethod(Mappings.LOCATION_DOMAIN);
        getEntityId = entityRoot.getMethod(Mappings.ENTITY_GET_ID);

        bindTexture = findDeclared(renderClass, Mappings.RENDER_BIND_TEXTURE, null, locationClass);

        if (bindTexture == null)
        {
            throw new IllegalStateException("bindTexture not resolved");
        }

        LogWrapper.info("[Vertex] Random entities armed");
    }

    /**
     * Finds a declared method by name and single parameter anywhere up the hierarchy.
     * paramAssignableFrom matches by assignability (entity types), paramExact by identity.
     */
    private static Method findDeclared(Class<?> owner, String name, Class<?> paramAssignableFrom, Class<?> paramExact)
    {
        for (Class<?> cls = owner; cls != null && cls != Object.class; cls = cls.getSuperclass())
        {
            for (Method method : cls.getDeclaredMethods())
            {
                if (!method.getName().equals(name) || method.getParameterTypes().length != 1)
                {
                    continue;
                }

                Class<?> param = method.getParameterTypes()[0];
                boolean matches = paramExact != null ? param == paramExact
                    : paramAssignableFrom != null && param.isAssignableFrom(paramAssignableFrom);

                if (matches)
                {
                    method.setAccessible(true);
                    return method;
                }
            }
        }

        return null;
    }

    private VertexRandomEntities()
    {
    }
}
