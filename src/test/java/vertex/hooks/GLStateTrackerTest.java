package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The skip decision's safety contract: unknown state never reads as redundant, known
 * repeats do, and every invalidation path (popAttrib, texture deletion, unit switches)
 * forces re-learning before another skip is possible.
 */
public final class GLStateTrackerTest
{
    private static final int GL_BLEND = 3042;
    private static final int GL_TEXTURE_2D = 3553;

    @Test
    public void unknownStateIsNeverRedundant()
    {
        GLStateTracker tracker = new GLStateTracker();
        assertFalse("first enable must forward", tracker.redundantEnable(GL_BLEND));
        assertFalse("first disable of another cap must forward", tracker.redundantDisable(2929));
        assertFalse("first bind must forward", tracker.redundantBind(GL_TEXTURE_2D, 7));
    }

    @Test
    public void knownRepeatsAreRedundantAndTransitionsAreNot()
    {
        GLStateTracker tracker = new GLStateTracker();
        tracker.redundantEnable(GL_BLEND);
        assertTrue(tracker.redundantEnable(GL_BLEND));
        assertFalse("enable to disable is a real transition", tracker.redundantDisable(GL_BLEND));
        assertTrue(tracker.redundantDisable(GL_BLEND));
        assertFalse(tracker.redundantEnable(GL_BLEND));

        tracker.redundantBind(GL_TEXTURE_2D, 7);
        assertTrue(tracker.redundantBind(GL_TEXTURE_2D, 7));
        assertFalse("a different texture is a real transition", tracker.redundantBind(GL_TEXTURE_2D, 8));
        assertFalse("returning to the old texture is a real transition", tracker.redundantBind(GL_TEXTURE_2D, 7));
    }

    @Test
    public void textureStateIsPerUnit()
    {
        GLStateTracker tracker = new GLStateTracker();
        tracker.redundantEnable(GL_TEXTURE_2D);
        tracker.redundantBind(GL_TEXTURE_2D, 7);

        tracker.setActiveUnit(1);
        assertFalse("another unit's texture cap is unknown", tracker.redundantEnable(GL_TEXTURE_2D));
        assertFalse("another unit's binding is unknown", tracker.redundantBind(GL_TEXTURE_2D, 7));

        tracker.setActiveUnit(0);
        assertTrue("unit zero still remembers its cap", tracker.redundantEnable(GL_TEXTURE_2D));
        assertTrue("unit zero still remembers its binding", tracker.redundantBind(GL_TEXTURE_2D, 7));
    }

    @Test
    public void texgenCapsAreAlsoPerUnit()
    {
        int genS = 3168;
        GLStateTracker tracker = new GLStateTracker();
        tracker.redundantEnable(genS);
        assertTrue(tracker.redundantEnable(genS));

        tracker.setActiveUnit(1);
        assertFalse("another unit's texgen enable must forward", tracker.redundantEnable(genS));

        tracker.setActiveUnit(0);
        assertTrue(tracker.redundantEnable(genS));
    }

    @Test
    public void popAttribInvalidatesEverything()
    {
        GLStateTracker tracker = new GLStateTracker();
        tracker.redundantEnable(GL_BLEND);
        tracker.redundantEnable(GL_TEXTURE_2D);
        tracker.redundantBind(GL_TEXTURE_2D, 7);

        tracker.invalidateAll();

        assertFalse(tracker.redundantEnable(GL_BLEND));
        assertFalse(tracker.redundantEnable(GL_TEXTURE_2D));
        assertFalse(tracker.redundantBind(GL_TEXTURE_2D, 7));
    }

    @Test
    public void deletedTexturesForceTheNextBindToForward()
    {
        GLStateTracker tracker = new GLStateTracker();
        tracker.redundantBind(GL_TEXTURE_2D, 7);
        tracker.forgetTexture(7);
        assertFalse("a reissued id must not read as bound", tracker.redundantBind(GL_TEXTURE_2D, 7));
        assertTrue(tracker.redundantBind(GL_TEXTURE_2D, 7));
    }
}
