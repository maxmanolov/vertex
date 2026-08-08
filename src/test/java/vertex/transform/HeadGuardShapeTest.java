package vertex.transform;

import org.junit.Before;
import org.junit.Test;
import vertex.TransformerHarness;
import vertex.TransformerHarness.ByteLoader;
import vertex.TransformerHarness.Probe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The (this, int, double) guard shape added for renderAllRenderLists interception. */
public class HeadGuardShapeTest
{
    private static final String PROBE = "vertex/TransformerHarness$Probe";

    @Before
    public void reset()
    {
        Probe.reset();
        Probe.guardResult = false;
        Probe.guardInt = -1;
        Probe.guardDouble = -1.0D;
    }

    @Test
    public void guardDeliversIntAndDoubleAndSkipsOnTrue() throws Exception
    {
        byte[] bytes = TransformerHarness.voidMethodClass("guard0", "run", "(ID)V");
        bytes = HeadGuardPatch.apply(bytes, "run", "(ID)V", PROBE, "guardIntDouble", HeadGuardPatch.THIS_INT_DOUBLE);
        Class<?> cls = new ByteLoader().add("guard0", bytes).loadClass("guard0");
        Object instance = cls.newInstance();

        Probe.guardResult = true;
        cls.getMethod("run", int.class, double.class).invoke(instance, 1, 0.25D);
        assertSame(instance, Probe.received);
        assertEquals(1, Probe.guardInt);
        assertEquals(0.25D, Probe.guardDouble, 0.0D);
        assertFalse("guard=true must skip the body", cls.getField("ran").getBoolean(null));

        Probe.guardResult = false;
        cls.getMethod("run", int.class, double.class).invoke(instance, 2, 0.5D);
        assertEquals(2, Probe.guardInt);
        assertEquals(0.5D, Probe.guardDouble, 0.0D);
        assertTrue("guard=false must run the body", cls.getField("ran").getBoolean(null));
    }
}
