package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vertex.ctm.CtmProperties;
import vertex.ctm.CtmRuleSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class VertexCtmDispatchTest
{
    public static final class FakeRegistry
    {
        public String c(Object block)
        {
            return "glass";
        }
    }

    public static final class FakeBlock
    {
        public static final FakeRegistry c = new FakeRegistry();

        public static int b(FakeBlock block)
        {
            return 20;
        }
    }

    public static final class FakeWorld
    {
        public FakeBlock a(int x, int y, int z)
        {
            return null;
        }
    }

    @Before
    public void resetBlockHandles() throws Exception
    {
        set("getBlockId", null);
        set("blockRegistry", null);
        set("getBlockRegistryName", null);
        set("getBlock", null);
        set("disabled", Boolean.FALSE);
        set("rules", null);
        ((Map<?, ?>)get("tiles")).clear();
        ((Map<?, ?>)get("blockKeyCache")).clear();
        ((Map<?, ?>)get("candidateCache")).clear();
    }

    @After
    public void cleanUp() throws Exception
    {
        resetBlockHandles();
    }

    @Test
    public void resolvesAndCachesNumericAndNamedBlockKeys() throws Exception
    {
        FakeBlock block = new FakeBlock();
        Object first = blockKeys(block);
        assertEquals("20", field(first, "id"));
        assertEquals("glass", field(first, "name"));
        assertSame(first, blockKeys(block));
    }

    @Test
    public void blockKeysFeedTheRenderCandidateCacheInPackOrder() throws Exception
    {
        CtmProperties numeric = rule("20", null, "0");
        CtmProperties named = rule("minecraft:glass", null, "1");
        CtmRuleSet rules = new CtmRuleSet();
        rules.add(numeric);
        rules.add(named);
        List<?> matching = candidates(rules, "unrelated_tile", new FakeBlock());
        assertEquals(2, matching.size());
        assertSame(numeric, matching.get(0));
        assertSame(named, matching.get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void substituteAppliesANumericBlockRuleWithoutATileMatch() throws Exception
    {
        CtmProperties blockRule = rule("20", null, "0-46");
        CtmRuleSet rules = new CtmRuleSet();
        rules.add(blockRule);
        Object connected = new Object();
        Object[] ruleTiles = new Object[47];
        Arrays.fill(ruleTiles, connected);
        set("rules", rules);
        ((Map<CtmProperties, Object[]>)get("tiles")).put(blockRule, ruleTiles);

        Object selected = VertexCtm.substitute(new Object(), "unrelated_tile", new FakeBlock(),
            new FakeWorld(), 0, 64, 0, 1);
        assertSame(connected, selected);
    }

    private static CtmProperties rule(String block, String tile, String output)
    {
        Properties props = new Properties();
        props.setProperty("matchBlocks", block);

        if (tile != null)
        {
            props.setProperty("matchTiles", tile);
        }

        props.setProperty("tiles", output);
        return new CtmProperties(props);
    }

    @SuppressWarnings("unchecked")
    private static List<CtmProperties> candidates(CtmRuleSet rules, String tile, Object block) throws Exception
    {
        Method method = VertexCtm.class.getDeclaredMethod("candidates", CtmRuleSet.class, String.class, Object.class);
        method.setAccessible(true);
        return (List<CtmProperties>)method.invoke(null, rules, tile, block);
    }

    private static Object blockKeys(Object block) throws Exception
    {
        Method method = VertexCtm.class.getDeclaredMethod("blockKeys", Object.class);
        method.setAccessible(true);
        return method.invoke(null, block);
    }

    private static Object field(Object owner, String name) throws Exception
    {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Object get(String name) throws Exception
    {
        Field field = VertexCtm.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void set(String name, Object value) throws Exception
    {
        Field field = VertexCtm.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
