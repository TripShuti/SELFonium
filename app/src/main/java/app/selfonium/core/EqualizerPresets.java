package app.selfonium.core;

/** Original tonal curves in dB; style names describe starting points, not hardware calibration. */
public final class EqualizerPresets {
    private EqualizerPresets(){}
    public static final String[] NAMES={"Рівний","Rock","Pop","Jazz","Classical","Dance","Electronic","Hip-Hop","Metal","Acoustic","Vocal","Bass boost","Treble boost","Loudness","Soft","Live","Reggae","Techno"};
    private static final int[][] DB={
        {0,0,0,0,0,0,0,0,0,0},{4,3,1,-1,-2,0,2,3,4,4},{-1,1,3,4,3,1,-1,-1,0,1},{3,2,1,1,-1,-1,0,1,2,3},
        {3,2,1,0,-1,-1,0,1,2,3},{5,4,2,0,-2,-1,1,3,4,3},{4,3,1,-1,-2,0,2,4,4,3},{5,4,2,1,-1,-1,0,1,2,2},
        {4,3,0,-2,-3,0,3,4,4,3},{2,2,1,0,1,2,3,2,1,1},{-3,-2,-1,1,3,4,3,1,-1,-2},{6,5,4,2,0,0,0,0,0,0},
        {0,0,0,0,0,1,2,3,4,5},{5,4,2,0,-1,0,1,2,4,5},{1,0,-1,-1,0,1,2,1,0,-1},{-1,0,1,2,2,1,2,3,2,1},
        {3,3,1,-1,-2,0,2,3,2,1},{5,4,1,-2,-2,0,2,4,4,3}
    };
    public static int[] gains(int index){int[] result=new int[10];for(int i=0;i<10;i++)result[i]=DB[index][i]*100;return result;}
    public static int match(int[] gains){for(int i=0;i<NAMES.length;i++)if(java.util.Arrays.equals(gains,gains(i)))return i;return -1;}
    public static int interpolate(java.util.SortedMap<Integer,Integer> old,int hz){
        if(old.isEmpty())return 0;Integer lower=null,upper=null;for(int frequency:old.keySet()){if(frequency<=hz)lower=frequency;if(frequency>=hz){upper=frequency;break;}}
        if(lower==null)return old.get(upper);if(upper==null)return old.get(lower);if(lower.equals(upper))return old.get(lower);
        double mix=Math.log(hz/(double)lower)/Math.log(upper/(double)lower);return (int)Math.round((old.get(lower)+(old.get(upper)-old.get(lower))*mix)/25)*25;
    }
}
