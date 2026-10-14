package app.selfonium.locale;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.test.InstrumentationTestCase;
import app.selfonium.R;
import java.util.Locale;

/** Resource-runtime checks; no settings writes, network requests or playback changes. */
@SuppressWarnings("deprecation")
public final class LocalizationTest extends InstrumentationTestCase {
    private Context localized(String tag){
        Context target=getInstrumentation().getTargetContext();
        Configuration config=new Configuration(target.getResources().getConfiguration());
        config.setLocales(new LocaleList(Locale.forLanguageTag(tag)));
        return target.createConfigurationContext(config);
    }
    public void testSupportedResourceSelectionAndFormatting(){
        String[] tags={"en","uk","pl","de","fr","es"};
        String[] titles={"Language","Мова","Język","Sprache","Langue","Idioma"};
        for(int i=0;i<tags.length;i++){
            Context context=localized(tags[i]);
            assertEquals(titles[i],context.getString(R.string.language_title));
            assertTrue(context.getString(R.string.delete_named,"My playlist").contains("My playlist"));
            assertTrue(context.getString(R.string.ui_custom_color_06x,0xABCDEF).contains("ABCDEF"));
            assertEquals(18,context.getResources().getStringArray(R.array.equalizer_presets).length);
        }
    }
    public void testUkrainianAndPolishQuantities(){
        assertEquals("1 композиція",localized("uk").getResources().getQuantityString(R.plurals.track_count,1,1));
        assertEquals("2 композиції",localized("uk").getResources().getQuantityString(R.plurals.track_count,2,2));
        assertEquals("11 композицій",localized("uk").getResources().getQuantityString(R.plurals.track_count,11,11));
        assertEquals("21 композиція",localized("uk").getResources().getQuantityString(R.plurals.track_count,21,21));
        assertEquals("1 utwór",localized("pl").getResources().getQuantityString(R.plurals.track_count,1,1));
        assertEquals("2 utwory",localized("pl").getResources().getQuantityString(R.plurals.track_count,2,2));
        assertEquals("21 utworów",localized("pl").getResources().getQuantityString(R.plurals.track_count,21,21));
    }
    public void testUserMetadataAndLiteralFormatTemplates(){
        Context context=getInstrumentation().getTargetContext();
        assertEquals("Мій плейлист",Texts.metadata(context,"Мій плейлист"));
        assertEquals("Rock",Texts.metadata(context,"Rock"));
        assertEquals("Artist",Texts.metadata(context,new StringBuilder("Artist")));
        assertEquals("",Texts.metadata(context,null));
        assertTrue(Texts.get(context,R.string.ui_custom_color_06x).contains("%06X"));
        assertFalse(Texts.get(context,R.string.ui_custom_color_06x,0xABCDEF).contains("%06X"));
    }
}
