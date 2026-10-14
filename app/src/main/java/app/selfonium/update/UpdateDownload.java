package app.selfonium.update;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.function.LongSupplier;
import static app.selfonium.update.UpdatePolicy.*;

public final class UpdateDownload {
    public interface Progress {void changed(long bytes,long total);}
    private UpdateDownload(){}
    public static String hex(byte[] data){StringBuilder out=new StringBuilder();for(byte b:data)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    public static String digest(File file,UpdateHttp.Cancel cancel)throws IOException{
        try(InputStream in=new FileInputStream(file)){MessageDigest sha=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){cancel.check();sha.update(buffer,0,n);}return hex(sha.digest());}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    public static File fetch(UpdateHttp http,ReleaseInfo release,File directory,UpdateHttp.Cancel cancel,Progress progress,LongSupplier space)throws IOException{
        File partial=new File(directory,"download.part"),candidate=new File(directory,"candidate.apk");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new Failure(Reason.STORAGE);
        partial.delete();candidate.delete();boolean complete=false;
        try{
            cancel.check();if(space.getAsLong()<release.size+RESERVE)throw new Failure(Reason.SPACE);
            try(UpdateHttp.Body body=http.open(release.apkUrl,cancel);InputStream in=body.input();OutputStream out=new FileOutputStream(partial)){
                if(body.length()!=-1&&body.length()!=release.size)throw new Failure(Reason.INTEGRITY);
                byte[] buffer=new byte[32768];long done=0,last=0;int n;
                while((n=in.read(buffer))!=-1){cancel.check();if(done+n>release.size)throw new Failure(Reason.INTEGRITY);if(space.getAsLong()<RESERVE+n)throw new Failure(Reason.SPACE);out.write(buffer,0,n);done+=n;long now=System.nanoTime();if(now-last>=200_000_000L||done==release.size){progress.changed(done,release.size);last=now;}}
                cancel.check();if(done!=release.size)throw new Failure(Reason.INTEGRITY);
            }
            if(!release.sha256.equals(digest(partial,cancel)))throw new Failure(Reason.INTEGRITY);
            Files.move(partial.toPath(),candidate.toPath(),StandardCopyOption.REPLACE_EXISTING);complete=true;return candidate;
        }catch(IOException e){cancel.check();if(e instanceof Failure)throw e;if(space.getAsLong()<RESERVE)throw new Failure(Reason.SPACE);throw new Failure(Reason.NETWORK);}
        finally{partial.delete();if(!complete)candidate.delete();}
    }
}
