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
    public Library library;
    public SourceStore sources;
    public SubsonicClient subsonic;
    public ArtworkRepository artwork;
    public SocialSync social;
    public AudioRepository audio;
    public final ExecutorService catalog = Executors.newSingleThreadExecutor();
    public final ExecutorService sync = Executors.newSingleThreadExecutor();
    public final ExecutorService io = Executors.newSingleThreadExecutor();
    public final Handler main = new Handler(Looper.getMainLooper());
    @Override public void onCreate() {
        super.onCreate();
        library = new Library(this); sources = new SourceStore(this); subsonic = new SubsonicClient(sources,message->{
            if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.d("SELFoniumSubsonic",message);
        });
        artwork = new ArtworkRepository(this, library, subsonic);
        audio=new AudioRepository(this,library,sources);
        social = new SocialSync(this);social.requestFlush();
        android.net.ConnectivityManager connectivity=getSystemService(android.net.ConnectivityManager.class);
        connectivity.registerDefaultNetworkCallback(new android.net.ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(android.net.Network network){social.networkAvailable();}
        });
    }
}
