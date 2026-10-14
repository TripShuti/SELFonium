package app.selfonium.locale;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import java.util.Locale;
import app.selfonium.core.LanguagePolicy;

/** Framework per-app locales on Android 13+, private preference on older Android. */
public final class AppLanguage {
    private AppLanguage(){}
    public static String selected(Context context){
        if(Build.VERSION.SDK_INT>=33){LocaleList list=context.getSystemService(LocaleManager.class).getApplicationLocales();return list.isEmpty()?"":list.get(0).getLanguage();}
        return context.getSharedPreferences("language",0).getString("selected","");
    }
    public static Locale locale(Context context){
        String choice=selected(context);LocaleList system=Resources.getSystem().getConfiguration().getLocales();String[] tags=new String[system.size()];for(int i=0;i<tags.length;i++)tags[i]=system.get(i).toLanguageTag();
        return Locale.forLanguageTag(LanguagePolicy.choose(choice,tags));
    }
    public static Context wrap(Context context){
        Locale locale=locale(context);if(locale.equals(context.getResources().getConfiguration().getLocales().get(0)))return context;
        Configuration config=new Configuration(context.getResources().getConfiguration());config.setLocales(new LocaleList(locale));return context.createConfigurationContext(config);
    }
    public static void initialize(Context context){
        if(Build.VERSION.SDK_INT>=33){var prefs=context.getSharedPreferences("language",0);if(!prefs.getBoolean("framework_migrated",false)){
            String legacy=prefs.getString("selected","");LocaleManager manager=context.getSystemService(LocaleManager.class);
            if(manager.getApplicationLocales().isEmpty()&&LanguagePolicy.supported(legacy))manager.setApplicationLocales(LocaleList.forLanguageTags(legacy));prefs.edit().putBoolean("framework_migrated",true).apply();
        }}
    }
    public static void apply(Activity activity,String tag){
        if(!tag.isEmpty()&&!LanguagePolicy.supported(tag))throw new IllegalArgumentException("Unsupported language");
        activity.getSharedPreferences("language",0).edit().putString("selected",tag).apply();
        if(Build.VERSION.SDK_INT>=33)activity.getSystemService(LocaleManager.class).setApplicationLocales(LocaleList.forLanguageTags(tag));else activity.recreate();
    }
}
