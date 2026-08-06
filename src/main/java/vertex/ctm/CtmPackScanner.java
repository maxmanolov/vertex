package vertex.ctm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Discovers CTM rule files in resource packs. Unlike the fixed-path pack features, CTM
 * uses arbitrary file names under assets/minecraft/mcpatcher/ctm/, so the packs must be
 * enumerated rather than probed. Both layouts vanilla supports are handled: a directory
 * pack and a zip pack. A malformed rule file is skipped rather than failing the scan, so
 * one bad file never costs a pack its remaining rules.
 *
 * Pure file I/O against a game directory - no Minecraft types, fully unit-testable.
 */
public final class CtmPackScanner
{
    private static final String CTM_DIR = "assets/minecraft/mcpatcher/ctm/";

    public interface Problem
    {
        void skipped(String source, Exception cause);
    }

    public static CtmRuleSet scan(File resourcePacksDir, String[] enabledPacks, Problem problems)
    {
        CtmRuleSet rules = new CtmRuleSet();

        if (resourcePacksDir == null || enabledPacks == null)
        {
            return rules;
        }

        for (String packName : enabledPacks)
        {
            File pack = new File(resourcePacksDir, packName);

            try
            {
                if (pack.isDirectory())
                {
                    scanDirectory(new File(pack, CTM_DIR), rules, problems);
                }
                else if (pack.isFile())
                {
                    scanZip(pack, rules, problems);
                }
            }
            catch (Exception e)
            {
                if (problems != null)
                {
                    problems.skipped(packName, e);
                }
            }
        }

        return rules;
    }

    private static void scanDirectory(File ctmDir, CtmRuleSet rules, Problem problems)
    {
        File[] files = ctmDir.listFiles();

        if (files == null)
        {
            return;
        }

        // Deterministic rule order regardless of filesystem enumeration (kyrofx #44);
        // matches the sorted order used for zip packs.
        java.util.Arrays.sort(files, new java.util.Comparator<File>()
        {
            public int compare(File a, File b)
            {
                return a.getName().compareTo(b.getName());
            }
        });

        for (File file : files)
        {
            if (file.isDirectory())
            {
                scanDirectory(file, rules, problems);
            }
            else if (file.getName().endsWith(".properties"))
            {
                InputStream in = null;

                try
                {
                    in = new java.io.FileInputStream(file);
                    CtmProperties rule = CtmProperties.load(in, stem(file.getName()));
                    rule.directory = relativeDir(file.getParentFile());
                    rules.add(rule);
                }
                catch (Exception e)
                {
                    if (problems != null)
                    {
                        problems.skipped(file.getPath(), e);
                    }
                }
                finally
                {
                    close(in);
                }
            }
        }
    }

    private static void scanZip(File pack, CtmRuleSet rules, Problem problems) throws IOException
    {
        ZipFile zip = new ZipFile(pack);

        try
        {
            // Collect matching entries first and sort by path so zip archive order never
            // decides rule precedence (kyrofx #44).
            java.util.List<ZipEntry> matching = new java.util.ArrayList<ZipEntry>();
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements())
            {
                ZipEntry candidate = entries.nextElement();

                if (!candidate.isDirectory() && candidate.getName().startsWith(CTM_DIR) && candidate.getName().endsWith(".properties"))
                {
                    matching.add(candidate);
                }
            }

            java.util.Collections.sort(matching, new java.util.Comparator<ZipEntry>()
            {
                public int compare(ZipEntry a, ZipEntry b)
                {
                    return a.getName().compareTo(b.getName());
                }
            });

            for (ZipEntry entry : matching)
            {
                String name = entry.getName();

                {
                    InputStream in = null;

                    try
                    {
                        in = zip.getInputStream(entry);
                        int slashIndex = name.lastIndexOf('/');
                        CtmProperties rule = CtmProperties.load(in, stem(name.substring(slashIndex + 1)));
                        int slash = name.lastIndexOf('/');
                        rule.directory = slash > 0 ? name.substring("assets/minecraft/".length(), slash) : null;
                        rules.add(rule);
                    }
                    catch (Exception e)
                    {
                        if (problems != null)
                        {
                            problems.skipped(pack.getName() + "!" + name, e);
                        }
                    }
                    finally
                    {
                        close(in);
                    }
                }
            }
        }
        finally
        {
            zip.close();
        }
    }

    private static String stem(String fileName)
    {
        return fileName.substring(0, fileName.length() - ".properties".length());
    }

    /** Pack-relative directory under assets/minecraft, e.g. mcpatcher/ctm/glass. */
    private static String relativeDir(File dir)
    {
        String path = dir.getPath().replace(File.separatorChar, '/');
        int marker = path.indexOf("assets/minecraft/");
        return marker >= 0 ? path.substring(marker + "assets/minecraft/".length()) : null;
    }

    private static void close(InputStream in)
    {
        if (in != null)
        {
            try
            {
                in.close();
            }
            catch (IOException ignored)
            {
            }
        }
    }

    private CtmPackScanner()
    {
    }
}
