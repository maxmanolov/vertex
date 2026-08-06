package vertex.ctm;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Parser for the community-documented CTM resource-pack format
 * (assets/minecraft/mcpatcher/ctm/*.properties). Implements the subset the render hook
 * will consume first: method, matchBlocks/matchTiles, tiles (with ranges), connect,
 * faces, metadata. Unknown keys are preserved-ignored so packs using extensions do not
 * fail to parse; malformed files throw and are skipped by the loader with a log line,
 * never crashing resource load.
 */
public final class CtmProperties
{
    public enum Method
    {
        CTM, HORIZONTAL, VERTICAL, TOP, RANDOM, REPEAT, FIXED;

        static Method parse(String value)
        {
            String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);

            if (normalized.equals("ctm") || normalized.equals("glass"))
            {
                return CTM;
            }
            else if (normalized.equals("horizontal") || normalized.equals("bookshelf"))
            {
                return HORIZONTAL;
            }
            else if (normalized.equals("vertical"))
            {
                return VERTICAL;
            }
            else if (normalized.equals("top"))
            {
                return TOP;
            }
            else if (normalized.equals("random"))
            {
                return RANDOM;
            }
            else if (normalized.equals("repeat"))
            {
                return REPEAT;
            }
            else if (normalized.equals("fixed"))
            {
                return FIXED;
            }

            throw new IllegalArgumentException("Unknown CTM method: " + value);
        }
    }

    public enum Connect
    {
        BLOCK, TILE, STATE;

        static Connect parse(String value)
        {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        }
    }

    public final Method method;
    public final List<String> matchBlocks;
    public final List<String> matchTiles;
    public final int[] tiles;
    public final Connect connect;
    public final int facesMask;
    public final int[] metadata;

    /** Pack-relative directory holding this rule's numbered tile images. */
    public String directory;

    /** Face bits, matching vanilla side ordering: DOWN, UP, NORTH, SOUTH, WEST, EAST. */
    public static final int FACE_BOTTOM = 1;
    public static final int FACE_TOP = 2;
    public static final int FACE_NORTH = 4;
    public static final int FACE_SOUTH = 8;
    public static final int FACE_WEST = 16;
    public static final int FACE_EAST = 32;
    public static final int FACE_ALL = 63;

    public static CtmProperties load(InputStream in) throws IOException
    {
        return load(in, null);
    }

    public static CtmProperties load(InputStream in, String defaultTile) throws IOException
    {
        Properties props = new Properties();
        props.load(in);
        return new CtmProperties(props, defaultTile);
    }

    public CtmProperties(Properties props)
    {
        this(props, null);
    }

    /**
     * defaultTile: the properties filename stem. Per the documented format, a rule that
     * declares neither matchBlocks nor matchTiles matches the tile named by its own file
     * (kyrofx #43).
     */
    public CtmProperties(Properties props, String defaultTile)
    {
        this.method = Method.parse(get(props, "method", "ctm"));
        this.matchBlocks = split(props.getProperty("matchBlocks"));
        this.matchTiles = split(props.getProperty("matchTiles"));

        if (this.matchBlocks.isEmpty() && this.matchTiles.isEmpty() && defaultTile != null && !defaultTile.isEmpty())
        {
            this.matchTiles.add(defaultTile);
        }

        this.tiles = parseIntList(props.getProperty("tiles"));
        this.connect = Connect.parse(get(props, "connect", this.matchTiles.isEmpty() ? "block" : "tile"));
        this.facesMask = parseFaces(props.getProperty("faces"));
        this.metadata = parseIntList(props.getProperty("metadata"));
    }

    private static String get(Properties props, String key, String fallback)
    {
        String value = props.getProperty(key);
        return value != null ? value : fallback;
    }

    private static List<String> split(String value)
    {
        List<String> out = new ArrayList<String>();

        if (value != null)
        {
            for (String token : value.trim().split("\\s+"))
            {
                if (!token.isEmpty())
                {
                    out.add(token);
                }
            }
        }

        return out;
    }

    /** Parses "0-11 16 18-20" style lists into expanded int arrays. */
    static int[] parseIntList(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return new int[0];
        }

        List<Integer> out = new ArrayList<Integer>();

        for (String token : value.trim().split("\\s+"))
        {
            int dash = token.indexOf('-', 1);

            if (dash > 0)
            {
                int from = Integer.parseInt(token.substring(0, dash));
                int to = Integer.parseInt(token.substring(dash + 1));

                for (int i = from; i <= to; ++i)
                {
                    out.add(Integer.valueOf(i));
                }
            }
            else
            {
                out.add(Integer.valueOf(Integer.parseInt(token)));
            }
        }

        int[] array = new int[out.size()];

        for (int i = 0; i < array.length; ++i)
        {
            array[i] = out.get(i).intValue();
        }

        return array;
    }

    static int parseFaces(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return FACE_ALL;
        }

        int mask = 0;

        for (String token : value.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+"))
        {
            if (token.equals("bottom"))
            {
                mask |= FACE_BOTTOM;
            }
            else if (token.equals("top"))
            {
                mask |= FACE_TOP;
            }
            else if (token.equals("north"))
            {
                mask |= FACE_NORTH;
            }
            else if (token.equals("south"))
            {
                mask |= FACE_SOUTH;
            }
            else if (token.equals("west"))
            {
                mask |= FACE_WEST;
            }
            else if (token.equals("east"))
            {
                mask |= FACE_EAST;
            }
            else if (token.equals("sides"))
            {
                mask |= FACE_NORTH | FACE_SOUTH | FACE_WEST | FACE_EAST;
            }
            else if (token.equals("all"))
            {
                mask |= FACE_ALL;
            }
        }

        return mask == 0 ? FACE_ALL : mask;
    }
}
