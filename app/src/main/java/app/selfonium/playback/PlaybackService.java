package app.selfonium.playback;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;
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
import app.selfonium.core.EqualizerCurve;
import app.selfonium.net.Providers;
import app.selfonium.ui.MainActivity;
import java.io.File;
import java.io.IOException;
import org.json.JSONArray;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
    private ExoPlayer player;private MediaSession session;private Equalizer equalizer;private int eqSession=-1;
    private SharedPreferences prefs;private SelfoniumApp app;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener settings=(p,key)->{if(!"queue".equals(key)&&!"position".equals(key)&&!"queueIndex".equals(key))applySettings();};
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(player==null)return;
        long end=prefs.getLong("sleepUntil",0);if(end>0&&System.currentTimeMillis()>=end){player.pause();prefs.edit().putLong("sleepUntil",0).apply();}
        saveQueue();applyEqualizer();handler.postDelayed(this,5000);
    }};
    public static MediaItem item(Track track) {
        return new MediaItem.Builder().setMediaId(track.id).setUri(Uri.parse("selfonium://track/"+Uri.encode(track.id)))
                .setMimeType(track.uri.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")?"application/x-mpegURL":null)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).build()).build();
    }
    @Override public void onCreate(){
        super.onCreate();getSharedPreferences("equalizer_caps",MODE_PRIVATE).edit().clear().apply();app=(SelfoniumApp)getApplication();prefs=getSharedPreferences("player",MODE_PRIVATE);
        var source=new ResolvingDataSource.Factory(new DefaultDataSource.Factory(this,SafeHttpDataSource::new),spec->{
            if(!"selfonium".equals(spec.uri.getScheme()))return spec;
            String id=spec.uri.getLastPathSegment();Track track=app.library.get(id);if(track==null)throw new IOException("Композицію видалено з бібліотеки");
            String local=app.library.offline(id);
            if(!local.isEmpty()&&new File(local).isFile())return spec.withUri(Uri.fromFile(new File(local)));
            try{Providers.Stream stream=app.providers.stream(track);return spec.withUri(Uri.parse(stream.url())).withRequestHeaders(stream.headers());}
            catch(Exception e){throw new IOException("Не вдалося відкрити аудіо. Перевірте джерело або синхронізуйте його повторно.",e);}
        });
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(source)).build();
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
    private void applyEqualizer(){
        int audioSession=player.getAudioSessionId();if(audioSession==C.AUDIO_SESSION_ID_UNSET)return;
        SharedPreferences caps=getSharedPreferences("equalizer_caps",MODE_PRIVATE);
        try{
            boolean changed=equalizer==null||eqSession!=audioSession;
            if(changed){if(equalizer!=null)equalizer.release();equalizer=null;equalizer=new Equalizer(0,audioSession);eqSession=audioSession;}
            short[] range=equalizer.getBandLevelRange();short bands=equalizer.getNumberOfBands();
            JSONArray frequencies=new JSONArray();
            for(short band=0;band<bands;band++){
                int hz=equalizer.getCenterFreq(band)/1000;frequencies.put(hz);
                int legacy=Math.min(4,(int)((long)band*5/Math.max(1,bands)));
                int gain=prefs.getInt("eqGain_"+hz,prefs.getInt("eq"+legacy,0));
                equalizer.setBandLevel(band,(short)EqualizerCurve.clamp(gain,range[0],range[1]));
            }
            equalizer.setEnabled(prefs.getBoolean("eqEnabled",false));
            if(changed||!caps.getBoolean("available",false))caps.edit().putBoolean("available",true).putString("frequencies",frequencies.toString()).putInt("min",range[0]).putInt("max",range[1]).apply();
        }catch(RuntimeException ignored){if(caps.getBoolean("available",false))caps.edit().putBoolean("available",false).apply();}
    }
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
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);prefs.unregisterOnSharedPreferenceChangeListener(settings);saveQueue();getSharedPreferences("equalizer_caps",MODE_PRIVATE).edit().putBoolean("available",false).apply();if(equalizer!=null)equalizer.release();if(session!=null)session.release();if(player!=null)player.release();player=null;super.onDestroy();}
}
