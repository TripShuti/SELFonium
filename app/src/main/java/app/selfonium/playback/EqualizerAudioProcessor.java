package app.selfonium.playback;

import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import app.selfonium.core.GraphicEqualizer;
import java.nio.ByteBuffer;

/** Always part of the PCM chain, so toggling EQ never rebuilds or seeks the player. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class EqualizerAudioProcessor extends BaseAudioProcessor {
    private volatile GraphicEqualizer.Config requested=new GraphicEqualizer.Config(false,new int[10],0,true);
    private GraphicEqualizer engine;public volatile long processedFrames;public volatile int sampleRate,channels;
    public void settings(GraphicEqualizer.Config config){requested=config;}
    @Override protected AudioFormat onConfigure(AudioFormat input) throws UnhandledAudioFormatException {
        if(input.encoding!=C.ENCODING_PCM_16BIT||input.channelCount<1||input.channelCount>32||input.sampleRate<8000)throw new UnhandledAudioFormatException(input);
        return input;
    }
    @Override protected void onFlush(){sampleRate=inputAudioFormat.sampleRate;channels=inputAudioFormat.channelCount;engine=sampleRate>0&&channels>0?new GraphicEqualizer(sampleRate,channels,requested):null;}
    @Override protected void onReset(){engine=null;sampleRate=0;channels=0;processedFrames=0;}
    @Override public void queueInput(ByteBuffer input){
        int bytes=input.remaining();if(bytes==0)return;engine.update(requested);ByteBuffer output=replaceOutputBuffer(bytes);
        while(input.remaining()>=channels*2){for(int channel=0;channel<channels;channel++){int lo=input.get()&255,hi=input.get();short value=(short)((hi<<8)|lo);double filtered=engine.sample(value/32768.0,channel);int pcm=(int)Math.round(Math.max(-1,Math.min(32767/32768.0,filtered))*32768);output.put((byte)pcm);output.put((byte)(pcm>>8));}processedFrames++;}
        output.flip();
    }
}
