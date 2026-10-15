package app.selfonium.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Real temporary files and streams, no Android, server or timing dependencies. */
public final class StorageTest {
    private static int checks;
    private static void check(boolean value,String detail){checks++;if(!value)throw new AssertionError(detail);}
    private static void failure(DownloadCopy.Problem wanted,InputStream in,long expected,java.util.function.BooleanSupplier cancel,java.util.function.LongSupplier space)throws Exception{
        try{DownloadCopy.copy(in,new ByteArrayOutputStream(),expected,cancel,space,(done,total)->{});throw new AssertionError("Expected "+wanted);}catch(DownloadCopy.Failure error){check(error.problem==wanted,"Download failure classification");}
    }
    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("selfonium-storage-test");
        try{
            Path offline=Files.createDirectory(folder.resolve("offline"));ManagedFiles files=new ManagedFiles(offline.toFile());
            Path source=folder.resolve("original.audio");Files.write(source,new byte[]{1,2,3,4});
            check(!files.owns(source.toFile()),"Original outside managed folder");check(!files.delete(source.toFile()),"Never delete original");check(Files.size(source)==4,"Original preserved");
            Path track=offline.resolve("track.audio");Files.write(track,new byte[]{1,2,3});
            ManagedFiles.Lease first=files.acquire(track.toFile()),second=files.acquire(track.toFile());check(first!=null&&second!=null,"Multiple playback readers");
            check(!files.delete(track.toFile()),"Active track retained");first.close();first.close();check(!files.delete(track.toFile()),"Other reader still active");
            Path partial=offline.resolve("track.part");Files.write(partial,new byte[]{9});check(!files.publish(partial.toFile(),track.toFile()),"Cannot replace active track");
            second.close();check(files.publish(partial.toFile(),track.toFile()),"Publish complete file");check(!Files.exists(partial)&&Files.size(track)==1,"Atomic publication removes partial");
            Path alias=offline.resolve("../offline/track.audio");ManagedFiles.Lease aliasLease=files.acquire(alias.toFile());check(aliasLease!=null&&!files.delete(track.toFile()),"Alias cannot bypass lease");aliasLease.close();
            Path link=offline.resolve("link.audio");Files.createSymbolicLink(link,source);check(!files.owns(link.toFile()),"Symlink rejected");check(files.acquire(link.toFile())==null,"No lease on external symlink");check(!files.delete(link.toFile()),"No delete through link");check(ManagedFiles.bytes(offline.toFile())==1,"Size does not follow links");
            Path directoryLink=folder.resolve("offline-link");Files.createSymbolicLink(directoryLink,offline);check(!new ManagedFiles(directoryLink.toFile()).owns(directoryLink.resolve("track.audio").toFile()),"Symlink root rejected");
            check(!files.publish(source.toFile(),track.toFile()),"Cannot move external original into cache");check(files.delete(track.toFile()),"Idle track removed");check(files.delete(track.toFile()),"Missing file deletion idempotent");
            check(ManagedFiles.saturatedAdd(Long.MAX_VALUE-1,100)==Long.MAX_VALUE,"Usage overflow saturated");
            byte[] audio=new byte[200000];new Random(19).nextBytes(audio);ByteArrayOutputStream output=new ByteArrayOutputStream();AtomicLong progress=new AtomicLong();
            DownloadCopy.Result result=DownloadCopy.copy(new ByteArrayInputStream(audio),output,audio.length,()->false,()->Long.MAX_VALUE,(done,total)->{check(total==audio.length&&done>progress.get(),"Monotonic progress with known length");progress.set(done);});
            check(result.bytes()==audio.length&&Arrays.equals(audio,output.toByteArray()),"Whole stream copied");check(progress.get()==audio.length,"Final progress");
            Path complete=offline.resolve("saved.audio");Files.write(complete,audio);check(result.sha256().equals(DownloadCopy.digest(complete.toFile())),"Recorded SHA-256 matches file");audio[0]^=1;Files.write(complete,audio);check(!result.sha256().equals(DownloadCopy.digest(complete.toFile())),"Damaged file detected");
            DownloadCopy.Result unknown=DownloadCopy.copy(new ByteArrayInputStream(new byte[]{1}),new ByteArrayOutputStream(),-1,()->false,()->Long.MAX_VALUE,(done,total)->check(total==-1,"Unknown size supported"));check(unknown.bytes()==1,"Unknown-length success");
            failure(DownloadCopy.Problem.TRUNCATED,new ByteArrayInputStream(new byte[]{1}),2,()->false,()->Long.MAX_VALUE);
            failure(DownloadCopy.Problem.TRUNCATED,new ByteArrayInputStream(new byte[]{1,2}),1,()->false,()->Long.MAX_VALUE);
            failure(DownloadCopy.Problem.EMPTY,new ByteArrayInputStream(new byte[0]),-1,()->false,()->Long.MAX_VALUE);
            failure(DownloadCopy.Problem.CANCELLED,new ByteArrayInputStream(new byte[]{1}),1,()->true,()->Long.MAX_VALUE);
            failure(DownloadCopy.Problem.SPACE,new ByteArrayInputStream(new byte[]{1}),1,()->false,()->DownloadCopy.RESERVE);
            failure(DownloadCopy.Problem.SPACE,new ByteArrayInputStream(new byte[]{1}),-1,()->false,()->DownloadCopy.RESERVE);
            AtomicBoolean cancelled=new AtomicBoolean();try{DownloadCopy.copy(new ByteArrayInputStream(new byte[70000]),new ByteArrayOutputStream(),-1,cancelled::get,()->Long.MAX_VALUE,(done,total)->cancelled.set(true));throw new AssertionError("Cancellation between buffers");}catch(DownloadCopy.Failure error){check(error.problem==DownloadCopy.Problem.CANCELLED,"Mid-download cancellation");}
            try{DownloadCopy.copy(new InputStream(){public int read()throws IOException{throw new IOException("Fixture connection lost");}},new ByteArrayOutputStream(),-1,()->false,()->Long.MAX_VALUE,(done,total)->{});throw new AssertionError("Network loss");}catch(IOException error){check(error.getMessage().equals("Fixture connection lost"),"I/O failure propagated");}
            check(Files.size(source)==4,"Original remains after all maintenance scenarios");
        }finally{try(var paths=Files.walk(folder)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        System.out.println("PASS: "+checks+" storage checks (leases, paths, symlinks, publication, size/hash, cancellation, space and interrupted streams)");
    }
}
