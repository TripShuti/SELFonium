package app.selfonium.playback;

import android.os.SystemClock;
import androidx.media3.common.*;
import app.selfonium.SelfoniumApp;
import app.selfonium.core.*;
import app.selfonium.data.Library;
import app.selfonium.data.ScrobbleRepository;

/** Main-player-thread adapter; sampling and checkpoints perform no network work. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackScrobbler {
    private final SelfoniumApp app;private final Player player;private ListeningSession session;private Source source;private Track track;
    private long duration,position,lastReport,lastSave;private String state="";private Library.Checkpoint restore;private boolean enabled,completed;private int configurationRevision;
    PlaybackScrobbler(SelfoniumApp app,Player player){this.app=app;this.player=player;enabled=app.scrobbles.enabled();configurationRevision=app.scrobbles.configurationRevision();}
    void restore(Library.Checkpoint checkpoint){restore=checkpoint;}
    void transition(MediaItem item){
        finish();completed=false;track=item==null?null:app.library.get(item.mediaId);if(track==null||java.util.List.of("device","files","radio").contains(track.sourceId)){restore=null;return;}
        try{source=app.sources.get(track.sourceId);}catch(Exception error){source=null;}if(source==null){restore=null;return;}
        duration=track.durationMs;position=Math.max(0,player.getCurrentPosition());long now=SystemClock.elapsedRealtime();
        if(restore!=null&&restore.state().track().equals(track.id)&&restore.account().equals(ListeningSession.account(source))&&System.currentTimeMillis()-restore.state().started()<ListeningSession.RETENTION)session=new ListeningSession(restore.state(),now);else session=new ListeningSession(track.id,now);
        restore=null;state="";lastSave=lastReport=0;sample();
    }
    void sample(){sample(false);}
    void sample(boolean forceReport){
        boolean allowed=app.scrobbles.enabled();if(allowed!=enabled||configurationRevision!=app.scrobbles.configurationRevision()){configurationRevision=app.scrobbles.configurationRevision();enabled=allowed;session=null;if(allowed)transition(player.getCurrentMediaItem());return;}
        if(session==null||!enabled||player.getCurrentMediaItem()==null||!player.getCurrentMediaItem().mediaId.equals(session.state().track()))return;
        if(completed&&player.getPlaybackState()!=Player.STATE_ENDED&&player.isPlaying()){transition(player.getCurrentMediaItem());return;}if(player.getPlaybackState()==Player.STATE_ENDED)completed=true;long now=SystemClock.elapsedRealtime();session.advance(now,System.currentTimeMillis(),player.isPlaying());
        if(player.getDuration()>0)duration=player.getDuration();position=Math.max(0,player.getCurrentPosition());
        String current=player.getPlaybackState()==Player.STATE_ENDED||player.getPlaybackState()==Player.STATE_IDLE?"stopped":player.isPlaying()?"playing":"paused";
        if(session.eligible(duration)||!session.state().submitted()&&player.isPlaying()&&now-lastSave>=5000||forceReport||!current.equals(state)){app.scrobbles.save(session,source,track.remoteId,duration);lastSave=now;}
        if(session.state().started()>0&&(forceReport||!current.equals(state)||now-lastReport>=30_000)){report(current,now);lastReport=now;}state=current;
    }
    void discontinuity(Player.PositionInfo old,Player.PositionInfo next){if(session!=null&&old.mediaItem!=null&&old.mediaItem.mediaId.equals(session.state().track()))position=Math.max(0,old.positionMs);}
    private void report(String status,long now){ListeningSession.State snapshot=session.state();app.scrobbles.report(new ScrobbleRepository.Report(snapshot.session(),source.id,track.remoteId,ListeningSession.account(source),snapshot.started(),status,position,player.getPlaybackParameters().speed,now));}
    void finish(){if(session!=null&&enabled){long now=SystemClock.elapsedRealtime();session.advance(now,System.currentTimeMillis(),false);app.scrobbles.save(session,source,track.remoteId,duration);if(session.state().started()>0)report("stopped",now);}session=null;source=null;track=null;}
}
