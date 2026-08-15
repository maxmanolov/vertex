package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Per-animation and per-particle gates behind the Animations page.
 *
 * Texture side: TextureMap.updateAnimations reroutes each sprite's updateAnimation call
 * through {@link #updateSprite}; a head hook records which atlas is updating (blocks or
 * items) so the per-type keys apply to terrain sprites while the items atlas has its own
 * master. The global master (textureAnimations) stays a whole-method skip, so a frozen
 * game keeps its zero-cost path.
 *
 * Particle side: every string-named ambient particle funnels through RenderGlobal's
 * doSpawnParticle; the head guard suppresses by name family. Vanilla returns null from
 * that method itself (distance culling), so a suppressed spawn is indistinguishable from
 * a culled one to every caller.
 *
 * A reflective failure disables the gates permanently in the vanilla direction: sprites
 * keep animating, particles keep spawning.
 */
public final class VertexAnimations
{
    static final int ATLAS_BLOCKS = 0;
    static final int ATLAS_ITEMS = 1;

    private static boolean disabled = false;

    private static Method spriteUpdate;
    private static Method spriteName;
    private static Field atlasType;
    private static int currentAtlas = ATLAS_BLOCKS;

    public static long suppressedSpriteUpdates = 0L;
    public static long suppressedParticles = 0L;

    /** Head of TextureMap.updateAnimations: remember which atlas is updating. */
    public static void beginAtlasUpdate(Object textureMap)
    {
        try
        {
            if (atlasType == null)
            {
                atlasType = textureMap.getClass().getDeclaredField(Mappings.TM_TYPE);
                atlasType.setAccessible(true);
            }

            currentAtlas = atlasType.getInt(textureMap);
        }
        catch (Throwable t)
        {
            currentAtlas = ATLAS_BLOCKS;
            disable("beginAtlasUpdate", t);
        }
    }

    /** Reroute of TextureAtlasSprite.updateAnimation inside updateAnimations. */
    public static void updateSprite(Object sprite)
    {
        try
        {
            if (spriteUpdate == null)
            {
                spriteUpdate = sprite.getClass().getMethod(Mappings.SPRITE_UPDATE);
                spriteUpdate.setAccessible(true);
                spriteName = sprite.getClass().getMethod(Mappings.SPRITE_NAME);
                spriteName.setAccessible(true);
            }

            if (!disabled)
            {
                String name = String.valueOf(spriteName.invoke(sprite));

                if (!spriteAllowed(name, currentAtlas,
                    VertexConfig.enabled("terrainAnimated"), VertexConfig.enabled("itemsAnimated"),
                    VertexConfig.enabled("animWater"), VertexConfig.enabled("animLava"),
                    VertexConfig.enabled("animFire"), VertexConfig.enabled("animPortal")))
                {
                    ++suppressedSpriteUpdates;
                    return;
                }
            }

            spriteUpdate.invoke(sprite);
        }
        catch (Throwable t)
        {
            disable("updateSprite", t);
            lastResortUpdate(sprite);
        }
    }

    /** Head guard on doSpawnParticle: true suppresses the spawn (the method returns null). */
    public static boolean interceptParticle(Object renderGlobal, Object name)
    {
        if (disabled)
        {
            return false;
        }

        boolean suppress = suppressedParticle(String.valueOf(name));

        if (suppress)
        {
            ++suppressedParticles;
        }

        return suppress;
    }

    // ---- pure decision logic (unit-tested) -------------------------------------------

    /**
     * Blocks-atlas sprites pass the terrain master plus their per-type key; unmatched
     * terrain sprites follow the master alone. Items-atlas sprites (compass, clock) only
     * consult the items master.
     */
    static boolean spriteAllowed(String name, int atlas, boolean terrain, boolean items,
        boolean water, boolean lava, boolean fire, boolean portal)
    {
        if (atlas == ATLAS_ITEMS)
        {
            return items;
        }

        if (!terrain)
        {
            return false;
        }

        if (name == null)
        {
            return true;
        }

        if (name.contains("water_still") || name.contains("water_flow"))
        {
            return water;
        }

        if (name.contains("lava_still") || name.contains("lava_flow"))
        {
            return lava;
        }

        if (name.contains("fire_layer"))
        {
            return fire;
        }

        if (name.contains("portal"))
        {
            return portal;
        }

        return true;
    }

    /** True when this spawn's gating key exists and is currently off. */
    static boolean suppressedParticle(String name)
    {
        String key = particleKey(name);
        return key != null && !VertexConfig.enabled(key);
    }

    /** Name-family to config-key mapping; null means the particle is never gated. */
    static String particleKey(String name)
    {
        if (name == null)
        {
            return null;
        }

        if (name.equals("explode") || name.equals("largeexplode") || name.equals("hugeexplosion"))
        {
            return "particleExplosions";
        }

        if (name.equals("smoke") || name.equals("largesmoke"))
        {
            return "particleSmoke";
        }

        if (name.equals("portal"))
        {
            return "particlePortal";
        }

        if (name.equals("flame"))
        {
            return "particleFlame";
        }

        if (name.equals("splash") || name.equals("bubble"))
        {
            return "particleWater";
        }

        if (name.equals("dripWater") || name.equals("dripLava"))
        {
            return "particleDripping";
        }

        if (name.equals("spell") || name.equals("instantSpell") || name.equals("mobSpell")
            || name.equals("mobSpellAmbient") || name.equals("witchMagic"))
        {
            return "particlePotion";
        }

        return null;
    }

    // ---- plumbing --------------------------------------------------------------------

    private static void lastResortUpdate(Object sprite)
    {
        try
        {
            if (spriteUpdate != null)
            {
                spriteUpdate.invoke(sprite);
            }
        }
        catch (Throwable ignored)
        {
            // One sprite misses one tick of animation; the next frame retries.
        }
    }

    private static void disable(String where, Throwable t)
    {
        if (!disabled)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Animation gates disabled after failure in " + where);
            t.printStackTrace();
        }
    }

    private VertexAnimations()
    {
    }
}
