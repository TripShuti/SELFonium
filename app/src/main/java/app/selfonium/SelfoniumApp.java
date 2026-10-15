package app.selfonium;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import app.selfonium.data.Library;
import app.selfonium.data.SourceStore;
import app.selfonium.data.ArtworkRepository;
import app.selfonium.data.SocialSync;
import app.selfonium.data.AudioRepository;
import app.selfonium.net.SubsonicClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SelfoniumApp extends Application {
    public app.selfonium.update.UpdateManager updates;
    public Library library;
    public SourceStore sources;
    public SubsonicClient subsonic;
    public ArtworkRepository artwork;
    public SocialSync social;
    public AudioRepository audio;
    public app.selfonium.data.ScrobbleRepository scrobbles;
    public app.selfonium.data.OfflineStore offline;
    public app.selfonium.data.DownloadRepository downloads;
    public app.selfonium.data.StorageRepository storage;
    public final ExecutorService catalog = Executors.newSingleThreadExecutor();
    public final ExecutorService sync = Executors.newSingleThreadExecutor();
    public final ExecutorService io = Executors.newSingleThreadExecutor();
    public final Handler main = new Handler(Looper.getMainLooper());
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(app.selfonium.locale.AppLanguage.wrap(base));}
    @Override public void onCreate() {
        super.onCreate();
        app.selfonium.locale.AppLanguage.initialize(this);
        library = new Library(this); sources = new SourceStore(this); subsonic = new SubsonicClient(sources,message->{
            if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.d("SELFoniumSubsonic",message);
        });
        artwork = new ArtworkRepository(this, library, subsonic);
        offline=new app.selfonium.data.OfflineStore(this,library);
        audio=new AudioRepository(this,library,sources);
        downloads=new app.selfonium.data.DownloadRepository(this);
        storage=new app.selfonium.data.StorageRepository(this);
        social = new SocialSync(this);social.requestFlush();
        scrobbles=new app.selfonium.data.ScrobbleRepository(this);
        updates=new app.selfonium.update.UpdateManager(this);
        android.net.ConnectivityManager connectivity=getSystemService(android.net.ConnectivityManager.class);
        connectivity.registerDefaultNetworkCallback(new android.net.ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(android.net.Network network){social.networkAvailable();scrobbles.networkAvailable();main.post(downloads::networkChanged);}
            @Override public void onCapabilitiesChanged(android.net.Network network,android.net.NetworkCapabilities capabilities){main.post(downloads::networkChanged);}
            @Override public void onLost(android.net.Network network){main.post(downloads::networkChanged);}
        });
    }
}
