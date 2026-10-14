package app.selfonium.core;

import java.util.List;
import java.util.Locale;

public final class LanguagePolicy {
    public static final List<String> TAGS=List.of("en","uk","pl","de","fr","es");
    private LanguagePolicy(){}
    public static boolean supported(String tag){return tag!=null&&TAGS.contains(Locale.forLanguageTag(tag).getLanguage());}
    public static String choose(String override,String...system){
        if(supported(override))return override;
        for(String tag:system)if(supported(tag))return tag;
        return "en";
    }
}
