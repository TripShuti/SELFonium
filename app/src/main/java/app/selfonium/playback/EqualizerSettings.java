package app.selfonium.playback;

import android.content.SharedPreferences;
import app.selfonium.core.GraphicEqualizer;
import app.selfonium.core.EqualizerPresets;
import java.util.TreeMap;

public final class EqualizerSettings {
    private EqualizerSettings(){}
    public static void migrate(SharedPreferences prefs){
        if(prefs.getBoolean("dspEqMigrated",false))return;TreeMap<Integer,Integer> old=new TreeMap<>();
        for(var entry:prefs.getAll().entrySet())if(entry.getKey().matches("eqGain_[0-9]+")&&entry.getValue() instanceof Integer){int hz=Integer.parseInt(entry.getKey().substring(7));if(hz>0)old.put(hz,(Integer)entry.getValue());}
        if(old.isEmpty()){int[] hz={60,230,910,3600,14000};for(int i=0;i<5;i++)old.put(hz[i],prefs.getInt("eq"+i,0));}
        SharedPreferences.Editor edit=prefs.edit();for(int i=0;i<10;i++)edit.putInt("dspEq_"+i,app.selfonium.core.EqualizerCurve.clamp(EqualizerPresets.interpolate(old,GraphicEqualizer.FREQUENCIES[i]),GraphicEqualizer.MIN,GraphicEqualizer.MAX));edit.putBoolean("dspEqMigrated",true).apply();
    }
    public static GraphicEqualizer.Config read(SharedPreferences prefs){int[] gains=new int[10];for(int i=0;i<10;i++)gains[i]=prefs.getInt("dspEq_"+i,0);return new GraphicEqualizer.Config(prefs.getBoolean("eqEnabled",false),gains,prefs.getInt("dspPreamp",0),prefs.getBoolean("dspHeadroom",true));}
    public static void save(SharedPreferences prefs,int[] gains,int preamp){SharedPreferences.Editor edit=prefs.edit();for(int i=0;i<10;i++)edit.putInt("dspEq_"+i,gains[i]);edit.putInt("dspPreamp",preamp).apply();}
}
