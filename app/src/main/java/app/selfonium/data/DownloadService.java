package app.selfonium.data;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import android.text.format.Formatter;
import app.selfonium.R;
import app.selfonium.SelfoniumApp;
import app.selfonium.core.Track;
import app.selfonium.locale.Texts;
import app.selfonium.ui.MainActivity;
import java.util.concurrent.*;

/** Foreground only while transferring; waiting/failed entries live in the durable queue. */
public final class DownloadService extends Service {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private DownloadRepository downloads;private volatile boolean stopped;private boolean draining;
    private final Runnable changed=this::notifyProgress;
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(app.selfonium.locale.AppLanguage.wrap(base));}
    @Override public void onCreate(){super.onCreate();downloads=((SelfoniumApp)getApplication()).downloads;getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("downloads",Texts.get(this,R.string.ui_offline_music),NotificationManager.IMPORTANCE_LOW));downloads.listen(changed);}
    private Notification notification(String text,boolean active){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel=PendingIntent.getService(this,1,new Intent(this,DownloadService.class).setAction("cancel"),PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,"downloads").setSmallIcon(R.drawable.ic_selfonium).setContentTitle(Texts.get(this,R.string.ui_selfonium_offline)).setContentText(text).setContentIntent(open).setOngoing(active).setOnlyAlertOnce(true);
        if(active)builder.addAction(new Notification.Action.Builder((android.graphics.drawable.Icon)null,Texts.get(this,R.string.ui_cancel),cancel).build());
        for(DownloadRepository.Entry entry:downloads.entries())if(entry.status()==DownloadRepository.Status.RUNNING){if(entry.total()>0)builder.setProgress(100,(int)Math.min(100,100.0*entry.bytes()/entry.total()),false);else builder.setProgress(0,0,true);break;}
        return builder.build();
    }
    private void notifyProgress(){
        if(stopped)return;SelfoniumApp app=(SelfoniumApp)getApplication();
        for(DownloadRepository.Entry entry:downloads.entries())if(entry.status()==DownloadRepository.Status.RUNNING){Track track=app.library.get(entry.id());String size=Formatter.formatShortFileSize(this,entry.bytes());getSystemService(NotificationManager.class).notify(42,notification((track==null?"":track.title+" · ")+size,true));return;}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(42,notification(Texts.get(this,R.string.ui_downloading),true));
        if(intent!=null&&"cancel".equals(intent.getAction()))downloads.cancelAll();
        if(!draining){draining=true;worker.execute(()->{try{while(!stopped&&!Thread.currentThread().isInterrupted()&&downloads.runNext()){} }finally{new android.os.Handler(getMainLooper()).post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});}});}
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){stopped=true;downloads.unlisten(changed);downloads.interrupt();downloads.serviceStopped();worker.shutdownNow();super.onDestroy();}
    @Override public void onTimeout(int startId,int foregroundServiceType){stopped=true;downloads.interrupt();worker.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public IBinder onBind(Intent intent){return null;}
}
