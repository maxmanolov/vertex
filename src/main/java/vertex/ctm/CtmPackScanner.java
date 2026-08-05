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
                    CtmProperties rule = CtmProperties.load(in);
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
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!entry.isDirectory() && name.startsWith(CTM_DIR) && name.endsWith(".properties"))
                {
                    InputStream in = null;

                    try
                    {
                        in = zip.getInputStream(entry);
                        CtmProperties rule = CtmProperties.load(in);
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
