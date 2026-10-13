package app.selfonium.core;

import java.util.Arrays;

/** Ten octave-spaced peaking filters. RBJ Audio EQ Cookbook, double-precision state. */
public final class GraphicEqualizer {
    public static final int[] FREQUENCIES={31,63,125,250,500,1000,2000,4000,8000,16000};
    public static final int MIN=-1200,MAX=1200;
    public static final class Config {
        public final boolean enabled,headroom;public final int preamp;private final int[] gains;
        public Config(boolean enabled,int[] gains,int preamp,boolean headroom){
            if(gains.length!=10)throw new IllegalArgumentException("Ten bands required");this.enabled=enabled;this.headroom=headroom;this.preamp=EqualizerCurve.clamp(preamp,MIN,MAX);this.gains=gains.clone();for(int i=0;i<10;i++)this.gains[i]=EqualizerCurve.clamp(this.gains[i],MIN,MAX);
        }
        public int gain(int band){return gains[band];}public int[] gains(){return gains.clone();}
        @Override public boolean equals(Object other){return other instanceof Config c&&enabled==c.enabled&&headroom==c.headroom&&preamp==c.preamp&&Arrays.equals(gains,c.gains);}
        @Override public int hashCode(){return Arrays.hashCode(gains)*31+preamp+(enabled?1:0)+(headroom?2:0);}
    }
    private int rate,channels,fade,fadeLength;private Config config;private Engine engine,previous;
    public GraphicEqualizer(int sampleRate,int channels,Config config){if(sampleRate<8000||channels<1||channels>32)throw new IllegalArgumentException("Unsupported PCM format");this.rate=sampleRate;this.channels=channels;this.config=config;engine=new Engine(rate,channels,config);fadeLength=Math.max(1,rate/100);}
    public void update(Config value){if(config.equals(value))return;config=value;previous=engine;engine=new Engine(rate,channels,value);fade=fadeLength;}
    public void reset(){engine=new Engine(rate,channels,config);previous=null;fade=0;}
    /** Call once per channel, in interleaved frame order. No allocation on the sample path. */
    public double sample(double input,int channel){
        double output=engine.sample(input,channel);
        if(fade>0&&previous!=null){double old=previous.sample(input,channel),mix=1-fade/(double)fadeLength;output=old+(output-old)*mix;if(channel==channels-1&&--fade==0)previous=null;}
        return Double.isFinite(output)?output:0;
    }
    public static double responseDb(Config config,int rate,double hz){if(!config.enabled)return 0;double value=config.preamp/100.0;for(int i=0;i<10;i++)value+=response(coefficients(rate,FREQUENCIES[i],config.gain(i)),rate,hz);return value;}
    public static double attenuationDb(Config config,int rate){
        if(!config.enabled||!config.headroom)return 0;double[][] filters=new double[10][];for(int i=0;i<10;i++)filters[i]=coefficients(rate,FREQUENCIES[i],config.gain(i));
        double peak=0;for(int i=0;i<=384;i++){double hz=20*Math.pow(Math.min(20000,rate*.49)/20.0,i/384.0),value=config.preamp/100.0;for(double[] filter:filters)value+=response(filter,rate,hz);peak=Math.max(peak,value);}return peak>0?peak+.5:0;
    }
    private static double[] coefficients(int rate,int hz,int gain){
        if(gain==0||hz>=rate*.475)return new double[]{1,0,0,0,0};
        double a=Math.pow(10,gain/4000.0),w=2*Math.PI*hz/rate,alpha=Math.sin(w)/(2*Math.sqrt(2)),cos=Math.cos(w),a0=1+alpha/a;
        return new double[]{(1+alpha*a)/a0,-2*cos/a0,(1-alpha*a)/a0,-2*cos/a0,(1-alpha/a)/a0};
    }
    private static double response(double[] c,int rate,double hz){
        double w=2*Math.PI*hz/rate,cos=Math.cos(w),sin=Math.sin(w),cos2=Math.cos(2*w),sin2=Math.sin(2*w);
        double nr=c[0]+c[1]*cos+c[2]*cos2,ni=-c[1]*sin-c[2]*sin2,dr=1+c[3]*cos+c[4]*cos2,di=-c[3]*sin-c[4]*sin2;
        return 10*Math.log10(Math.max(1e-24,(nr*nr+ni*ni)/(dr*dr+di*di)));
    }
    private static final class Engine {
        final double[][] c=new double[10][];final double[][][] state;final double volume;final boolean enabled;
        Engine(int rate,int channels,Config config){enabled=config.enabled;state=new double[channels][10][4];for(int i=0;i<10;i++)c[i]=coefficients(rate,FREQUENCIES[i],config.gain(i));volume=Math.pow(10,(config.preamp/100.0-attenuationDb(config,rate))/20);}
        double sample(double input,int channel){if(!enabled)return input;double value=input*volume;for(int i=0;i<10;i++){double[] k=c[i],s=state[channel][i];double out=k[0]*value+k[1]*s[0]+k[2]*s[1]-k[3]*s[2]-k[4]*s[3];s[1]=s[0];s[0]=value;s[3]=s[2];s[2]=out;value=out;}return value;}
    }
}
