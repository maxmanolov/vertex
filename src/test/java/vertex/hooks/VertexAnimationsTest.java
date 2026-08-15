package vertex.hooks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The animation gates' pure decision logic: sprite gating (masters and per-type keys),
 * the particle name-family mapping, and the contract that the All ON/OFF scope is
 * exactly the set of Vertex keys wired on the Animations page.
 */
public final class VertexAnimationsTest
{
    private static final int B = VertexAnimations.ATLAS_BLOCKS;
    private static final int I = VertexAnimations.ATLAS_ITEMS;

    @Test
    public void itemsAtlasOnlyConsultsTheItemsMaster()
    {
        assertTrue(VertexAnimations.spriteAllowed("compass", I, false, true, false, false, false, false));
        assertFalse(VertexAnimations.spriteAllowed("clock", I, true, false, true, true, true, true));
    }

    @Test
    public void terrainMasterGatesEverythingOnTheBlocksAtlas()
    {
        assertFalse(VertexAnimations.spriteAllowed("water_still", B, false, true, true, true, true, true));
        assertTrue(VertexAnimations.spriteAllowed("water_still", B, true, true, true, true, true, true));
    }

    @Test
    public void perTypeKeysGateTheirFamiliesAndUnmatchedSpritesFollowTheMaster()
    {
        assertFalse(VertexAnimations.spriteAllowed("water_flow", B, true, true, false, true, true, true));
        assertFalse(VertexAnimations.spriteAllowed("lava_still", B, true, true, true, false, true, true));
        assertFalse(VertexAnimations.spriteAllowed("fire_layer_0", B, true, true, true, true, false, true));
        assertFalse(VertexAnimations.spriteAllowed("portal", B, true, true, true, true, true, false));
        // A modded/unknown animated sprite only answers to the terrain master.
        assertTrue(VertexAnimations.spriteAllowed("sea_lantern", B, true, true, false, false, false, false));
        assertTrue(VertexAnimations.spriteAllowed(null, B, true, true, false, false, false, false));
    }

    @Test
    public void particleFamiliesMapToTheirKeysAndUnknownNamesAreNeverGated()
    {
        assertEquals("particleExplosions", VertexAnimations.particleKey("explode"));
        assertEquals("particleExplosions", VertexAnimations.particleKey("largeexplode"));
        assertEquals("particleExplosions", VertexAnimations.particleKey("hugeexplosion"));
        assertEquals("particleSmoke", VertexAnimations.particleKey("smoke"));
        assertEquals("particleSmoke", VertexAnimations.particleKey("largesmoke"));
        assertEquals("particlePortal", VertexAnimations.particleKey("portal"));
        assertEquals("particleFlame", VertexAnimations.particleKey("flame"));
        assertEquals("particleWater", VertexAnimations.particleKey("splash"));
        assertEquals("particleWater", VertexAnimations.particleKey("bubble"));
        assertEquals("particleDripping", VertexAnimations.particleKey("dripWater"));
        assertEquals("particleDripping", VertexAnimations.particleKey("dripLava"));
        assertEquals("particlePotion", VertexAnimations.particleKey("spell"));
        assertEquals("particlePotion", VertexAnimations.particleKey("witchMagic"));

        // Damage indicators, criticals, item pickups, block breaking: never gated.
        assertNull(VertexAnimations.particleKey("crit"));
        assertNull(VertexAnimations.particleKey("magicCrit"));
        assertNull(VertexAnimations.particleKey("blockcrack_1_0"));
        assertNull(VertexAnimations.particleKey(null));
    }

    @Test
    public void allOnOffScopeIsExactlyTheAnimationsPageVertexKeys()
    {
        List<VideoMenuLayout.Placed> page =
            VideoMenuLayout.layout(VideoMenuLayout.PAGE_ANIMATIONS, 400, 300);
        Set<String> wired = new HashSet<String>();

        for (VideoMenuLayout.Placed slot : page)
        {
            if (slot.kind == VideoMenuLayout.KIND_VERTEX)
            {
                wired.add(slot.ref);
            }
        }

        Set<String> scope = new HashSet<String>(Arrays.asList(VideoMenuLayout.animationKeys()));
        assertEquals("All ON/OFF must flip exactly what the page shows", wired, scope);
        assertEquals("no duplicate keys in the scope",
            VideoMenuLayout.animationKeys().length, scope.size());
    }

    @Test
    public void everyAnimationKeyIsDeclaredWithADefaultOfTrue()
    {
        for (String key : VideoMenuLayout.animationKeys())
        {
            assertTrue("declared default of " + key + " is vanilla (true)",
                VertexConfig.declaredDefault(key));
        }
    }
}
