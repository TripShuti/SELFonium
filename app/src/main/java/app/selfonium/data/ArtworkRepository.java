package app.selfonium.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import app.selfonium.core.Protocol;
import app.selfonium.core.Track;
import app.selfonium.net.Http;
import app.selfonium.net.SubsonicClient;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Shared artwork loader: bounded workers, request coalescing and credential-free cache names. */
public final class ArtworkRepository {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final long DISK_LIMIT = 96L * 1024 * 1024;
    private final Context context;
    private final Library library;
    private final SubsonicClient providers;
    private final File directory;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), new ThreadPoolExecutor.AbortPolicy());
    private final LruCache<String, Bitmap> memory = new LruCache<>(32 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private final ThreadPoolExecutor foreground=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),new ThreadPoolExecutor.AbortPolicy());
    private final Map<String,Runnable> jobs=new HashMap<>();
    private final Map<String, List<Ticket>> pending = new HashMap<>();
    private final LinkedHashMap<String, Long> failures = new LinkedHashMap<>();

    public static final class Ticket {
        private volatile Consumer<Bitmap> callback;
        Ticket(Consumer<Bitmap> callback) { this.callback = callback; }
        public void cancel() { callback = null; }
        void deliver(Bitmap bitmap) { Consumer<Bitmap> target = callback; callback = null; if (target != null) target.accept(bitmap); }
    }
    public ArtworkRepository(Context context, Library library, SubsonicClient providers) {
        this.context = context.getApplicationContext(); this.library = library; this.providers = providers;
        directory = new File(context.getCacheDir(), "artwork-v1");
    }
    private String key(Track track) {
        return track.sourceId + "\n" + (track.artwork.isEmpty() ? "track:" + track.remoteId : "cover:" + track.artwork);
    }
    public Bitmap peek(Track track){return track==null?null:memory.get(key(track));}
    public Ticket load(Track track,Consumer<Bitmap> callback){return load(track,callback,false);}
    public Ticket load(Track track, Consumer<Bitmap> callback,boolean urgent) {
        Ticket ticket = new Ticket(callback);
        String key = key(track);
        Bitmap cached = memory.get(key);
        if (cached != null) { main.post(() -> ticket.deliver(cached)); return ticket; }
        synchronized (pending) {
            Long failed = failures.get(key);
            if (failed != null && System.currentTimeMillis() - failed < 30_000) {
                main.post(() -> ticket.deliver(null)); return ticket;
            }
            List<Ticket> waiting = pending.get(key);
            if (waiting != null) { waiting.add(ticket);Runnable queued=jobs.get(key);if(urgent&&queued!=null&&workers.remove(queued)){try{foreground.execute(queued);}catch(RejectedExecutionException busy){finish(key,null);}}return ticket; }
            pending.put(key, new ArrayList<>(List.of(ticket)));
        }
        Runnable job=() -> {
                synchronized(pending){List<Ticket> listeners=pending.get(key);if(listeners==null||listeners.stream().noneMatch(t->t.callback!=null)){pending.remove(key);jobs.remove(key);return;}}
                Bitmap result = null;
                try { result = read(track, key); } catch (Exception ignored) { /* A missing cover never interrupts audio. */ }
                if (result != null) memory.put(key, result);
                finish(key, result);
            };
        synchronized(pending){jobs.put(key,job);}
        try{if(urgent)foreground.execute(job);else workers.execute(job);}
        catch(RejectedExecutionException busy){finish(key,null);}
        return ticket;
    }
    private void finish(String key, Bitmap bitmap) {
        List<Ticket> listeners;
        synchronized (pending) {
            listeners = pending.remove(key);jobs.remove(key);
            if (bitmap == null) {
                failures.put(key, System.currentTimeMillis());
                while (failures.size() > 256) failures.remove(failures.keySet().iterator().next());
            } else failures.remove(key);
        }
        if (listeners != null) main.post(() -> { for (Ticket ticket : listeners) ticket.deliver(bitmap); });
    }
    public void retryFailures() { synchronized (pending) { failures.clear(); } }
    private File file(String key) throws Exception {
        return new File(directory, Protocol.hex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))) + ".img");
    }
    private Bitmap read(Track original, String key) throws Exception {
        File cached = file(key);
        if (cached.isFile() && cached.length() <= MAX_BYTES) {
            Bitmap bitmap = decode(Files.readAllBytes(cached.toPath()));
            if (bitmap != null) { cached.setLastModified(System.currentTimeMillis()); return bitmap; }
            cached.delete();
        }
        Track latest = library.get(original.id);
        Track track = latest == null ? original : latest;
        String currentKey = key(track);
        if (!key.equals(currentKey)) {
            Bitmap shared = memory.get(currentKey);
            if (shared != null) return shared;
            File album = file(currentKey);
            if (album.isFile() && album.length() <= MAX_BYTES) {
                shared = decode(Files.readAllBytes(album.toPath()));
                if (shared != null) { album.setLastModified(System.currentTimeMillis()); return shared; }
            }
        }
        byte[] bytes = null;
        String offline = library.offline(track.id);
        String local = !offline.isEmpty() && new File(offline).isFile() ? Uri.fromFile(new File(offline)).toString() : track.uri;
        if (local.startsWith("content:") || local.startsWith("file:")) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try { retriever.setDataSource(context, Uri.parse(local)); bytes = retriever.getEmbeddedPicture(); }
            catch (Exception ignored) {}
            finally { try { retriever.release(); } catch (Exception ignored) {} }
        }
        if (bytes == null) {
            SubsonicClient.Artwork artwork = providers.artwork(track);
            if (artwork == null) return null;
            if (!artwork.reference().equals(track.artwork)) library.artwork(track.id, artwork.reference());
            try (Http.Response response = Http.open("GET", artwork.stream().url(), artwork.stream().headers(), null);
                 InputStream input = response.input(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (response.length() > MAX_BYTES) return null;
                byte[] buffer = new byte[16_384]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_BYTES) return null;
                    output.write(buffer, 0, count);
                }
                bytes = output.toByteArray();
            }
            // Store both the legacy track key and the resolved shared album key.
            String resolvedKey = key(track.withArtwork(artwork.reference()));
            Bitmap bitmap = decode(bytes);
            if (bitmap == null) return null;
            memory.put(resolvedKey, bitmap);
            write(resolvedKey, bytes); if (!key.equals(resolvedKey)) write(key, bytes);
            return bitmap;
        }
        Bitmap bitmap = decode(bytes);
        if (bitmap != null) write(key, bytes);
        return bitmap;
    }
    private static Bitmap decode(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) return null;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
    private synchronized void write(String key, byte[] bytes) {
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) return;
            File target = file(key), temporary = File.createTempFile("cover-", ".part", directory);
            try {
                Files.write(temporary.toPath(), bytes);
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally { temporary.delete(); }
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".img"));
            if (files == null) return;
            long size = 0; for (File f : files) size += f.length();
            if (size > DISK_LIMIT) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                for (File f : files) { if (size <= DISK_LIMIT) break; long length = f.length(); if (f.delete()) size -= length; }
            }
        } catch (Exception ignored) { /* The memory cache remains usable when disk is full. */ }
    }
}
