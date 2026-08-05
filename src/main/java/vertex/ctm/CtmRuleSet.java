package vertex.ctm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed CTM rules indexed for O(1) lookup on the render path. Rules are grouped by the
 * key they match on - block id for connect=block, tile name for connect=tile - so a face
 * lookup is one map get and, at most, a short list walk over rules sharing that key.
 * Immutable once built.
 */
public final class CtmRuleSet
{
    private final Map<String, List<CtmProperties>> byBlock = new HashMap<String, List<CtmProperties>>();
    private final Map<String, List<CtmProperties>> byTile = new HashMap<String, List<CtmProperties>>();
    private final List<CtmProperties> all = new ArrayList<CtmProperties>();
    private int count;

    public void add(CtmProperties rule)
    {
        for (String block : rule.matchBlocks)
        {
            index(this.byBlock, block, rule);
        }

        for (String tile : rule.matchTiles)
        {
            index(this.byTile, tile, rule);
        }

        this.all.add(rule);
        ++this.count;
    }

    private static void index(Map<String, List<CtmProperties>> map, String key, CtmProperties rule)
    {
        List<CtmProperties> list = map.get(key);

        if (list == null)
        {
            list = new ArrayList<CtmProperties>(1);
            map.put(key, list);
        }

        list.add(rule);
    }

    /** Rules matching a sprite name, or null when the pack has none (the common case). */
    public List<CtmProperties> forTile(String tileName)
    {
        return this.byTile.get(tileName);
    }

    public List<CtmProperties> forBlock(String blockId)
    {
        return this.byBlock.get(blockId);
    }

    /** All rules in insertion order, for registration passes. */
    public List<CtmProperties> allRules()
    {
        return this.all;
    }

    public boolean isEmpty()
    {
        return this.count == 0;
    }

    public int size()
    {
        return this.count;
    }
}
