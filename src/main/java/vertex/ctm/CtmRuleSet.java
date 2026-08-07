package vertex.ctm;

import java.util.ArrayList;
import java.util.Collections;
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
            index(this.byBlock, normalizeBlockKey(block), rule);
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

        if (!list.contains(rule))
        {
            list.add(rule);
        }
    }

    /** Rules matching a sprite name, or null when the pack has none (the common case). */
    public List<CtmProperties> forTile(String tileName)
    {
        return this.byTile.get(tileName);
    }

    public List<CtmProperties> forBlock(String blockId)
    {
        return this.byBlock.get(normalizeBlockKey(blockId));
    }

    /**
     * Rules that match any current face key, in original pack order and without duplicates.
     * Callers cache the result per block and tile pair so the render path keeps its short
     * candidate-list walk instead of scanning the complete ruleset for every face.
     */
    public List<CtmProperties> matching(String tileName, String blockId, String blockName)
    {
        String normalizedId = normalizeBlockKey(blockId);
        String normalizedName = normalizeBlockKey(blockName);
        List<CtmProperties> matching = new ArrayList<CtmProperties>();

        for (CtmProperties rule : this.all)
        {
            if (contains(rule.matchTiles, tileName)
                || containsBlock(rule.matchBlocks, normalizedId, normalizedName))
            {
                matching.add(rule);
            }
        }

        return matching.isEmpty() ? Collections.<CtmProperties>emptyList()
            : Collections.unmodifiableList(matching);
    }

    private static boolean contains(List<String> values, String expected)
    {
        return expected != null && values.contains(expected);
    }

    private static boolean containsBlock(List<String> values, String id, String name)
    {
        for (String value : values)
        {
            String normalized = normalizeBlockKey(value);

            if (normalized.equals(id) || normalized.equals(name))
            {
                return true;
            }
        }

        return false;
    }

    private static String normalizeBlockKey(String value)
    {
        if (value == null)
        {
            return null;
        }

        String normalized = value.trim();
        return normalized.startsWith("minecraft:") ? normalized.substring("minecraft:".length()) : normalized;
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
