package vertex.ctm;

import java.util.Locale;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vertex.variants.NaturalProperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** The reporter's scenario: parsers must not depend on the default locale (Turkish I). */
public class LocaleSafetyTest
{
    private Locale saved;

    @Before
    public void turkish()
    {
        this.saved = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
    }

    @After
    public void restore()
    {
        Locale.setDefault(this.saved);
    }

    @Test
    public void ctmKeywordsParseUnderTurkishLocale()
    {
        Properties props = new Properties();
        props.setProperty("method", "FIXED");
        props.setProperty("matchTiles", "stone");
        props.setProperty("connect", "tile");
        CtmProperties parsed = new CtmProperties(props);
        assertEquals(CtmProperties.Method.FIXED, parsed.method);
        assertEquals(CtmProperties.Connect.TILE, parsed.connect);
    }

    @Test
    public void naturalSpecsParseUnderTurkishLocale()
    {
        Properties props = new Properties();
        props.setProperty("dirt", "f");
        assertNotNull(new NaturalProperties(props).spec("dirt"));
    }
}
