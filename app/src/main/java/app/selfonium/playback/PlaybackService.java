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
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import android.util.Log;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
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
    private androidx.media3.datasource.DataSource.Factory upstream;
    private long transitionAt;private int transition,playlistGeneration;
    private final Runnable prefetch=()->{
        if(player==null||!player.getPlayWhenReady()||player.getCurrentMediaItem()==null)return;
        java.util.List<Track> upcoming=new java.util.ArrayList<>();
        int at=player.getNextMediaItemIndex();
        if(at!=C.INDEX_UNSET){Track next=app.library.get(player.getMediaItemAt(at).mediaId);if(next!=null){upcoming.add(next);app.artwork.load(next,image->{});}}
        app.audio.prefetch(upstream,upcoming);
    };
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
        upstream=()->new ResolvingDataSource(new DefaultDataSource.Factory(this,SafeHttpDataSource::new).createDataSource(),new ResolvingDataSource.Resolver(){
            private Uri original;
            @Override public Uri resolveReportedUri(Uri uri){return original==null?uri:original;}
            @Override public androidx.media3.datasource.DataSpec resolveDataSpec(androidx.media3.datasource.DataSpec spec) throws IOException {
            original=spec.uri;
            if(!"selfonium".equals(spec.uri.getScheme()))return spec;
            String id=spec.uri.getLastPathSegment();Track track=app.library.get(id);if(track==null)throw new IOException("Композицію видалено з бібліотеки");
            String local=app.library.offline(id);
            if(!local.isEmpty()&&new File(local).isFile())return spec.withUri(Uri.fromFile(new File(local)));
            try{Providers.Stream stream=app.providers.stream(track);return spec.withUri(Uri.parse(stream.url())).withRequestHeaders(stream.headers());}
            catch(Exception e){throw new IOException("Не вдалося відкрити аудіо. Перевірте джерело або синхронізуйте його повторно.",e);}
            }
        });
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(this){@Override protected AudioSink buildAudioSink(android.content.Context context,boolean floatOutput,boolean playbackParameters){AudioSink sink=new DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setEnableAudioTrackPlaybackParams(false).setAudioProcessors(new AudioProcessor[]{equalizer}).build();return new ForwardingAudioSink(sink){@Override public boolean supportsFormat(Format format){return MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)&&super.supportsFormat(format);}@Override public int getFormatSupport(Format format){return MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)?super.getFormatSupport(format):SINK_FORMAT_UNSUPPORTED;}};}};
        player=new ExoPlayer.Builder(this,renderers).setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(15_000,45_000,500,1500).setPrioritizeTimeOverSizeThresholds(true).build()).setMediaSourceFactory(new DefaultMediaSourceFactory(app.audio.cached(upstream))).build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setAudioOffloadPreferences(new TrackSelectionParameters.AudioOffloadPreferences.Builder().setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED).build()).build());
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);
        player.setHandleAudioBecomingNoisy(true);player.setWakeMode(C.WAKE_MODE_LOCAL);
        PendingIntent activity=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        session=new MediaSession.Builder(this,player).setSessionActivity(activity).build();
        restoreQueue();applySettings();prefs.registerOnSharedPreferenceChangeListener(settings);
        player.addListener(new Player.Listener(){
            @Override public void onTimelineChanged(Timeline timeline,int reason){if(reason==Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED){playlistGeneration++;saveTimeline();}}
            @Override public void onMediaItemTransition(MediaItem item,int reason){
                transitionAt=android.os.SystemClock.elapsedRealtime();transition++;Log.i("SELFoniumPlayback","transition="+transition);
                handler.removeCallbacks(prefetch);app.audio.cancelPrefetch();
                if(item!=null){app.io.execute(()->app.library.played(item.mediaId));handler.postDelayed(prefetch,350);}
                saveQueue();
            }
            @Override public void onPlaybackStateChanged(int state){
                applyEqualizer();
                if(state==Player.STATE_READY)Log.i("SELFoniumPlayback","ready transition="+transition+" elapsedMs="+(android.os.SystemClock.elapsedRealtime()-transitionAt));
            }
            @Override public void onPlayWhenReadyChanged(boolean ready,int reason){Log.i("SELFoniumPlayback","playWhenReady="+ready+" reason="+reason);}
            @Override public void onPlaybackSuppressionReasonChanged(int reason){Log.i("SELFoniumPlayback","suppression="+reason);}
            @Override public void onIsPlayingChanged(boolean playing){Log.i("SELFoniumPlayback","playing="+playing);if(playing){handler.removeCallbacks(prefetch);handler.postDelayed(prefetch,350);}}
        });
        player.addAnalyticsListener(new AnalyticsListener(){
            @Override public void onAudioUnderrun(EventTime eventTime,int bufferSize,long bufferSizeMs,long elapsedSinceLastFeedMs){Log.w("SELFoniumPlayback","audio-underrun elapsedSinceFeedMs="+elapsedSinceLastFeedMs);}
        });handler.postDelayed(tick,5000);
    }
    private void applySettings(){if(player==null)return;player.setPlaybackSpeed(prefs.getFloat("speed",1));player.setShuffleModeEnabled(prefs.getBoolean("shuffle",false));player.setRepeatMode(prefs.getInt("repeat",Player.REPEAT_MODE_OFF));applyEqualizer();}
    private void applyEqualizer(){equalizer.settings(EqualizerSettings.read(prefs));}
    private void saveTimeline(){
        if(player==null)return;
        java.util.List<String> ids=new java.util.ArrayList<>();for(int i=0;i<player.getMediaItemCount();i++)ids.add(player.getMediaItemAt(i).mediaId);
        app.io.execute(()->{JSONArray json=new JSONArray();for(String id:ids)json.put(id);prefs.edit().putString("queue",json.toString()).apply();});
        saveQueue();
    }
    private void saveQueue(){
        if(player==null)return;
        int index=Math.max(0,player.getCurrentMediaItemIndex());long position=Math.max(0,player.getCurrentPosition());
        app.io.execute(()->prefs.edit().putInt("queueIndex",index).putLong("position",position).apply());
    }
    private void restoreQueue(){
        String saved=prefs.getString("queue","[]");int previous=prefs.getInt("queueIndex",0);long position=prefs.getLong("position",0);
        app.io.execute(()->{try{
            JSONArray ids=new JSONArray(saved);java.util.List<MediaItem> items=new java.util.ArrayList<>();int index=0;
            for(int i=0;i<ids.length();i++){Track track=app.library.get(ids.getString(i));if(track!=null){if(i<previous)index++;items.add(item(track));}}
            int restored=Math.min(index,Math.max(0,items.size()-1));
            handler.post(()->{if(player!=null&&playlistGeneration==0&&!items.isEmpty())player.setMediaItems(items,restored,position);});
        }catch(Exception ignored){}});
    }
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo){return controllerInfo.getUid()==android.os.Process.myUid()||controllerInfo.isTrusted()?session:null;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);app.audio.cancelPrefetch();prefs.unregisterOnSharedPreferenceChangeListener(settings);saveQueue();getSharedPreferences("equalizer_caps",MODE_PRIVATE).edit().putBoolean("available",false).apply();if(session!=null)session.release();if(player!=null)player.release();player=null;super.onDestroy();}
}
