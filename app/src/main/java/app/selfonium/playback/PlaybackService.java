package app.selfonium.playback;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.TrackSelectionParameters;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import app.selfonium.SelfoniumApp;
import app.selfonium.core.Track;
import app.selfonium.net.Providers;
import app.selfonium.ui.MainActivity;
import java.io.File;
import java.io.IOException;
import org.json.JSONArray;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
    private ExoPlayer player;private MediaSession session;private final EqualizerAudioProcessor equalizer=new EqualizerAudioProcessor();
    private SharedPreferences prefs;private SelfoniumApp app;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener settings=(p,key)->{if(key!=null&&(key.startsWith("dspEq_")||key.equals("eqEnabled")||key.equals("dspPreamp")||key.equals("dspHeadroom")||key.equals("speed")||key.equals("shuffle")||key.equals("repeat")))applySettings();};
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(player==null)return;
        long end=prefs.getLong("sleepUntil",0);if(end>0&&System.currentTimeMillis()>=end){player.pause();prefs.edit().putLong("sleepUntil",0).apply();}
        saveQueue();getSharedPreferences("equalizer_caps",MODE_PRIVATE).edit().putInt("sampleRate",equalizer.sampleRate).putInt("channels",equalizer.channels).putLong("processedFrames",equalizer.processedFrames).apply();handler.postDelayed(this,5000);
    }};
    public static MediaItem item(Track track) {
        return new MediaItem.Builder().setMediaId(track.id).setUri(Uri.parse("selfonium://track/"+Uri.encode(track.id)))
                .setMimeType(track.uri.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")?"application/x-mpegURL":null)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).build()).build();
    }
    @Override public void onCreate(){
        super.onCreate();getSharedPreferences("equalizer_caps",MODE_PRIVATE).edit().clear().apply();app=(SelfoniumApp)getApplication();prefs=getSharedPreferences("player",MODE_PRIVATE);EqualizerSettings.migrate(prefs);equalizer.settings(EqualizerSettings.read(prefs));
        var source=new ResolvingDataSource.Factory(new DefaultDataSource.Factory(this,SafeHttpDataSource::new),spec->{
            if(!"selfonium".equals(spec.uri.getScheme()))return spec;
            String id=spec.uri.getLastPathSegment();Track track=app.library.get(id);if(track==null)throw new IOException("Композицію видалено з бібліотеки");
            String local=app.library.offline(id);
            if(!local.isEmpty()&&new File(local).isFile())return spec.withUri(Uri.fromFile(new File(local)));
            try{Providers.Stream stream=app.providers.stream(track);return spec.withUri(Uri.parse(stream.url())).withRequestHeaders(stream.headers());}
            catch(Exception e){throw new IOException("Не вдалося відкрити аудіо. Перевірте джерело або синхронізуйте його повторно.",e);}
        });
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(this){@Override protected AudioSink buildAudioSink(android.content.Context context,boolean floatOutput,boolean playbackParameters){AudioSink sink=new DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setEnableAudioTrackPlaybackParams(false).setAudioProcessors(new AudioProcessor[]{equalizer}).build();return new ForwardingAudioSink(sink){@Override public boolean supportsFormat(Format format){return MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)&&super.supportsFormat(format);}@Override public int getFormatSupport(Format format){return MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)?super.getFormatSupport(format):SINK_FORMAT_UNSUPPORTED;}};}};
        player=new ExoPlayer.Builder(this,renderers).setMediaSourceFactory(new DefaultMediaSourceFactory(source)).build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setAudioOffloadPreferences(new TrackSelectionParameters.AudioOffloadPreferences.Builder().setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED).build()).build());
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);
        player.setHandleAudioBecomingNoisy(true);player.setWakeMode(C.WAKE_MODE_LOCAL);
        PendingIntent activity=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        session=new MediaSession.Builder(this,player).setSessionActivity(activity).build();
        restoreQueue();applySettings();prefs.registerOnSharedPreferenceChangeListener(settings);
        player.addListener(new Player.Listener(){
            @Override public void onMediaItemTransition(MediaItem item,int reason){if(item!=null)app.io.execute(()->app.library.played(item.mediaId));saveQueue();}
            @Override public void onPlaybackStateChanged(int state){applyEqualizer();}
        });handler.post(tick);
    }
    private void applySettings(){if(player==null)return;player.setPlaybackSpeed(prefs.getFloat("speed",1));player.setShuffleModeEnabled(prefs.getBoolean("shuffle",false));player.setRepeatMode(prefs.getInt("repeat",Player.REPEAT_MODE_OFF));applyEqualizer();}
    private void applyEqualizer(){equalizer.settings(EqualizerSettings.read(prefs));}
    private void saveQueue(){
        if(player==null)return;
        if(player.getMediaItemCount()==0){if(!prefs.getString("queue","[]").equals("[]"))prefs.edit().putString("queue","[]").putInt("queueIndex",0).putLong("position",0).apply();return;}
        JSONArray ids=new JSONArray();for(int i=0;i<player.getMediaItemCount();i++)ids.put(player.getMediaItemAt(i).mediaId);
        prefs.edit().putString("queue",ids.toString()).putInt("queueIndex",player.getCurrentMediaItemIndex()).putLong("position",Math.max(0,player.getCurrentPosition())).apply();
    }
    private void restoreQueue(){
        try{
            JSONArray ids=new JSONArray(prefs.getString("queue","[]"));java.util.List<MediaItem> items=new java.util.ArrayList<>();int previous=prefs.getInt("queueIndex",0),index=0;
            for(int i=0;i<ids.length();i++){Track t=app.library.get(ids.getString(i));if(t!=null){if(i<previous)index++;items.add(item(t));}}
            if(!items.isEmpty())player.setMediaItems(items,Math.min(index,items.size()-1),prefs.getLong("position",0));
        }catch(Exception ignored){}
    }
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo){return controllerInfo.getUid()==android.os.Process.myUid()||controllerInfo.isTrusted()?session:null;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);prefs.unregisterOnSharedPreferenceChangeListener(settings);saveQueue();getSharedPreferences("equalizer_caps",MODE_PRIVATE).edit().putBoolean("available",false).apply();if(session!=null)session.release();if(player!=null)player.release();player=null;super.onDestroy();}
}
