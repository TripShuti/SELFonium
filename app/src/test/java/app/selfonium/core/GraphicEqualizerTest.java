package app.selfonium.core;

import java.util.TreeMap;

public final class GraphicEqualizerTest {
    private static int checks;
    private static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    public static void main(String[] args){
        int[] flat=new int[10];GraphicEqualizer engine=new GraphicEqualizer(48000,1,new GraphicEqualizer.Config(true,flat,0,false));
        boolean exact=true;for(int n=-32768;n<=32767;n++){double input=n/32768.0;exact&=engine.sample(input,0)==input;}check(exact,"Flat EQ is bit-exact for every PCM16 value");
        for(int rate:new int[]{44100,48000})for(int band=0;band<10;band++){
            int[] gains=new int[10];gains[band]=600;engine=new GraphicEqualizer(rate,2,new GraphicEqualizer.Config(true,gains,0,false));double inPower=0,outPower=0,rightPower=0;int hz=GraphicEqualizer.FREQUENCIES[band];
            for(int frame=0;frame<rate*2;frame++){double input=.05*Math.sin(2*Math.PI*hz*frame/rate),output=engine.sample(input,0),right=engine.sample(0,1);if(frame>=rate){inPower+=input*input;outPower+=output*output;rightPower+=right*right;}}
            double db=10*Math.log10(outPower/inPower);check(Math.abs(db-6)<.03,"Measured +6 dB at "+hz+" Hz / "+rate);check(rightPower==0,"Stereo isolation at "+hz);
        }
        engine=new GraphicEqualizer(48000,1,new GraphicEqualizer.Config(true,flat,-600,false));check(Math.abs(engine.sample(.5,0)-.5*Math.pow(10,-6/20.0))<1e-10,"Preamp measured attenuation");
        int[] boost=EqualizerPresets.gains(11);GraphicEqualizer.Config loud=new GraphicEqualizer.Config(true,boost,300,true);double attenuation=GraphicEqualizer.attenuationDb(loud,48000);check(attenuation>6,"Headroom accounts for preamp and overlapping filters");boolean safe=true;for(int i=0;i<1000;i++){double hz=20*Math.pow(1000,i/999.0);safe&=GraphicEqualizer.responseDb(loud,48000,hz)-attenuation<.01;}check(safe,"Compensated response stays below unity");
        engine=new GraphicEqualizer(8000,1,new GraphicEqualizer.Config(true,EqualizerPresets.gains(12),0,true));boolean finite=true;for(int i=0;i<16000;i++)finite&=Double.isFinite(engine.sample(i==0?1:0,0));check(finite,"Low-rate impulse response stays finite above Nyquist bands");
        engine=new GraphicEqualizer(48000,2,new GraphicEqualizer.Config(true,boost,0,false));for(int i=0;i<5000;i++){engine.sample(.4,0);engine.sample(0,1);}engine.update(new GraphicEqualizer.Config(false,boost,1200,false));for(int i=0;i<1000;i++){engine.sample(.2,0);engine.sample(-.2,1);}check(engine.sample(.123,0)==.123&&engine.sample(-.321,1)==-.321,"Disabled EQ is exact after live crossfade");
        engine=new GraphicEqualizer(48000,1,new GraphicEqualizer.Config(true,boost,0,false));engine.sample(1,0);engine.reset();check(engine.sample(0,0)==0,"Seek/flush clears filter history");
        int[] original=new int[10];GraphicEqualizer.Config immutable=new GraphicEqualizer.Config(true,original,0,false);original[0]=1000;int[] copy=immutable.gains();copy[1]=1000;check(immutable.gain(0)==0&&immutable.gain(1)==0,"Settings snapshots are immutable across audio/UI threads");
        GraphicEqualizer.Config clamped=new GraphicEqualizer.Config(true,new int[]{9999,-9999,0,0,0,0,0,0,0,0},9999,false);check(clamped.gain(0)==1200&&clamped.gain(1)==-1200&&clamped.preamp==1200,"Persisted out-of-range gains clamp safely");
        for(int i=0;i<EqualizerPresets.NAMES.length;i++)check(EqualizerPresets.gains(i).length==10&&EqualizerPresets.match(EqualizerPresets.gains(i))>=0,"Preset has ten matching bands: "+i);
        TreeMap<Integer,Integer> old=new TreeMap<>();old.put(100,0);old.put(400,600);check(EqualizerPresets.interpolate(old,200)==300,"Legacy curves interpolate in log-frequency space");check(EqualizerPresets.interpolate(old,50)==0&&EqualizerPresets.interpolate(old,800)==600,"Legacy curve endpoint migration");
        System.out.println("PASS: "+checks+" DSP checks (measured response, stereo isolation, preamp, headroom, live bypass, reset, migration)");
    }
}
