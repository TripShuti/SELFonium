package app.selfonium.locale;

import android.content.Context;
import android.content.res.TypedArray;
import app.selfonium.R;
import java.util.*;

public final class Texts {
    private static Map<String,Integer> legacy;
    private Texts(){}
    public static String get(Context context,int id,Object...args){Context localized=AppLanguage.wrap(context);return args.length==0?localized.getString(id):localized.getString(id,args);}
    public static String quantity(Context context,int id,int count){return AppLanguage.wrap(context).getResources().getQuantityString(id,count,count);}
    /** Only for known internal navigation/error tokens, never arbitrary user metadata. */
    public static String known(Context context,String value){
        if(value==null)return "";
        Integer id=keys(context).get(value);return id==null?value:get(context,id);
    }
    private static synchronized Map<String,Integer> keys(Context context){
        if(legacy==null){Map<String,Integer> map=new HashMap<>();String[] keys=context.getResources().getStringArray(R.array.legacy_text_keys);TypedArray ids=context.getResources().obtainTypedArray(R.array.legacy_text_ids);
            try{for(int i=0;i<keys.length;i++)map.put(keys[i],ids.getResourceId(i,0));}finally{ids.recycle();}legacy=Collections.unmodifiableMap(map);
        }return legacy;
    }
    public static String metadata(Context context,CharSequence raw){
        if(raw==null)return "";
        String value=raw.toString();
        // Old library snapshots store these synthetic missing-metadata placeholders.
        if(value.equals("Без назви")||value.equals("Невідомий виконавець")||value.equals("Без альбому"))return known(context,value);
        if(value.equals("Аудіофайл"))return get(context,R.string.ui_audio_file);
        if(value.equals("[Unknown Album]"))return get(context,R.string.ui_unknown_album);return value;
    }
    public static String progress(Context context,String value){
        if(value!=null){java.util.regex.Matcher match=java.util.regex.Pattern.compile("^(.*?)([0-9]+) композицій$").matcher(value);
            if(match.matches())try{return match.group(1)+quantity(context,R.plurals.track_count,Integer.parseInt(match.group(2)));}catch(NumberFormatException ignored){}
        }
        return known(context,value);
    }
}
