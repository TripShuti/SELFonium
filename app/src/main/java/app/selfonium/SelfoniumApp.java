package app.selfonium;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import app.selfonium.data.Library;
import app.selfonium.data.SourceStore;
import app.selfonium.data.ArtworkRepository;
import app.selfonium.net.Providers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SelfoniumApp extends Application {
    public Library library;
    public SourceStore sources;
    public Providers providers;
    public ArtworkRepository artwork;
    public final ExecutorService io = Executors.newSingleThreadExecutor();
    public final Handler main = new Handler(Looper.getMainLooper());
    @Override public void onCreate() {
        super.onCreate();
        library = new Library(this); sources = new SourceStore(this); providers = new Providers(sources);
        artwork = new ArtworkRepository(this, library, providers);
    }
}
