package app.selfonium.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import app.selfonium.R;
import app.selfonium.SelfoniumApp;
import app.selfonium.core.Protocol;
import app.selfonium.core.Track;
import app.selfonium.net.Http;
import app.selfonium.net.SubsonicClient;
import app.selfonium.ui.MainActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DownloadService extends Service {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private volatile boolean cancelled;private int pending;
    @Override public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("downloads","Офлайн-музика",NotificationManager.IMPORTANCE_LOW));}
    private Notification notification(String text,boolean active){
        var open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        var cancel=PendingIntent.getService(this,1,new Intent(this,DownloadService.class).setAction("cancel"),PendingIntent.FLAG_IMMUTABLE);
        var builder=new Notification.Builder(this,"downloads").setSmallIcon(R.drawable.ic_selfonium).setContentTitle("SELFonium · Офлайн").setContentText(text).setContentIntent(open).setOngoing(active);
        if(active)builder.addAction(new Notification.Action.Builder((android.graphics.drawable.Icon)null,"Скасувати",cancel).build());return builder.build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("cancel".equals(intent.getAction())){cancelled=true;worker.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
        String id=intent.getStringExtra("track");if(id==null){stopSelf();return START_NOT_STICKY;}
        startForeground(42,notification("Завантаження…",true));pending++;
        worker.execute(()->{String status;
            try{download(id);status="Збережено для офлайн-прослуховування";}catch(Exception e){status=cancelled?"Завантаження скасовано":"Не вдалося завантажити. Перевірте джерело та вільне місце.";}
            String result=status;new android.os.Handler(getMainLooper()).post(()->{pending--;if(pending==0){stopForeground(STOP_FOREGROUND_REMOVE);if(!cancelled)getSystemService(NotificationManager.class).notify(43,notification(result,false));stopSelf();}});
        });return START_NOT_STICKY;
    }
    private void download(String id)throws Exception{
        SelfoniumApp app=(SelfoniumApp)getApplication();Track track=app.library.get(id);if(track==null)throw new IllegalArgumentException();
        File folder=new File(getFilesDir(),"offline");if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException();
        String key=Protocol.hex(java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        File partial=new File(folder,key+".part"),dest=new File(folder,key+".audio");SubsonicClient.Stream stream=app.subsonic.stream(track);
        try{
            if(stream.url().startsWith("content:")||stream.url().startsWith("file:")){
                try(InputStream in=getContentResolver().openInputStream(android.net.Uri.parse(stream.url()));FileOutputStream out=new FileOutputStream(partial)){copy(in,out,-1,track.title);}
            }else try(Http.Response response=Http.open("GET",stream.url(),stream.headers(),null);InputStream in=response.input();FileOutputStream out=new FileOutputStream(partial)){copy(in,out,response.length(),track.title);}
            if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException();
            Files.move(partial.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);app.library.offline(id,dest.getAbsolutePath());
        }finally{if(partial.exists())partial.delete();}
    }
    private void copy(InputStream in,FileOutputStream out,long expected,String title)throws Exception{
        byte[] buffer=new byte[65536];long total=0,last=0;int n;
        while((n=in.read(buffer))!=-1){if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException();out.write(buffer,0,n);total+=n;
            long now=System.currentTimeMillis();if(now-last>1000){last=now;getSystemService(NotificationManager.class).notify(42,notification(title+" · "+(total/1048576)+" МБ",true));}}
        if(expected>=0&&total!=expected)throw new java.io.EOFException();out.getFD().sync();
    }
    @Override public void onDestroy(){cancelled=true;worker.shutdownNow();super.onDestroy();}
    @Override public void onTimeout(int startId,int foregroundServiceType){cancelled=true;worker.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public IBinder onBind(Intent intent){return null;}
}
