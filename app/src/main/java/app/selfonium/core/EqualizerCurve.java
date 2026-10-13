package app.selfonium.core;

/** Presets sampled at the actual Android effect frequencies; gains are millibels. */
public final class EqualizerCurve {
    private EqualizerCurve() {}
    public static int clamp(int gain,int min,int max){return Math.max(min,Math.min(max,gain));}
    public static int preset(int preset,int hz,int min,int max){
        double octave=Math.log(Math.max(20,hz)/1000.0)/Math.log(2);
        double gain=switch(preset){
            case 1->500/(1+Math.exp((octave+2)*1.6));
            case 2->350*Math.exp(-octave*octave/3)-150;
            case 3->400/(1+Math.exp(-(octave-1)*1.6));
            default->0;
        };
        return clamp((int)Math.round(gain/25)*25,min,max);
    }
}
