package app.selfonium.core;

import java.io.*;
import java.security.*;
import java.util.function.*;

/** Whole-file downloads: bounded buffers, cancellation, space checks and integrity metadata. */
public final class DownloadCopy {
    public static final long RESERVE=8L*1024*1024;
    public enum Problem {CANCELLED,SPACE,TRUNCATED,EMPTY}
    public static final class Failure extends IOException{
        public final Problem problem;public Failure(Problem problem){super(problem.name());this.problem=problem;}
    }
    public record Result(long bytes,String sha256){}
    public interface Progress{void update(long done,long total);}
    private DownloadCopy(){}
    public static Result copy(InputStream in,OutputStream out,long expected,BooleanSupplier cancel,LongSupplier space,Progress progress)throws IOException{
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
        long done=0;byte[] buffer=new byte[65536];
        if(expected>=0&&space.getAsLong()<ManagedFiles.saturatedAdd(expected,RESERVE))throw new Failure(Problem.SPACE);
        while(true){
            if(cancel.getAsBoolean()||Thread.currentThread().isInterrupted())throw new Failure(Problem.CANCELLED);
            int n=in.read(buffer);if(n<0)break;if(n==0)continue;
            if(space.getAsLong()<n+RESERVE)throw new Failure(Problem.SPACE);
            out.write(buffer,0,n);digest.update(buffer,0,n);done+=n;progress.update(done,expected);
            if(expected>=0&&done>expected)throw new Failure(Problem.TRUNCATED);
        }
        if(cancel.getAsBoolean()||Thread.currentThread().isInterrupted())throw new Failure(Problem.CANCELLED);
        if(done==0)throw new Failure(Problem.EMPTY);if(expected>=0&&done!=expected)throw new Failure(Problem.TRUNCATED);
        return new Result(done,Protocol.hex(digest.digest()));
    }
    public static String digest(File file)throws IOException{
        try(InputStream in=new FileInputStream(file)){MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)hash.update(buffer,0,n);return Protocol.hex(hash.digest());}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
