package app.selfonium.data;

import android.content.Context;
import android.content.SharedPreferences;
import app.selfonium.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Serializes offline references and file publication against playback leases. */
public final class OfflineStore {
    public final File directory;public final ManagedFiles files;
    private final Library library;private final SharedPreferences integrity;
    public OfflineStore(Context context,Library library){this.library=library;directory=new File(context.getFilesDir(),"offline");files=new ManagedFiles(directory);integrity=context.getSharedPreferences("offline_integrity",0);}
    public String key(String id){try{return Protocol.hex(MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    public File target(String id){return new File(directory,key(id)+".audio");}
    public File partial(String id){return new File(directory,key(id)+".part");}
    public record Read(File file,ManagedFiles.Lease lease){}
    public synchronized Read open(String id){String path=library.offline(id);File file=new File(path);ManagedFiles.Lease lease=path.isEmpty()?null:files.acquire(file);return lease==null?null:new Read(file,lease);}
    public synchronized ManagedFiles.Lease acquire(String id){String path=library.offline(id);return path.isEmpty()?null:files.acquire(new File(path));}
    public synchronized boolean available(String id){String path=library.offline(id);File file=new File(path);return !path.isEmpty()&&files.owns(file)&&file.isFile()&&file.length()>0;}
    public synchronized boolean remove(String id){
        String path=library.offline(id);if(path.isEmpty())return true;File file=new File(path);
        if(!files.delete(file))return false;library.offline(id,"");integrity.edit().remove(key(id)).apply();return true;
    }
    public synchronized void publish(String id,DownloadCopy.Result result)throws IOException{
        if(!files.publish(partial(id),target(id)))throw new IOException("Offline file is in use");
        if(library.get(id)==null){files.delete(target(id));return;}
        library.offline(id,target(id).getAbsolutePath());integrity.edit().putString(key(id),result.bytes()+":"+result.sha256()).apply();
    }
    public record Verification(List<Track> damaged,int retained){}
    public Verification verify()throws IOException{
        List<Track> damaged=new ArrayList<>();int retained=0;
        for(Track track:library.offlineTracks()){
            String path,record;ManagedFiles.Lease lease;File file;
            synchronized(this){path=library.offline(track.id);record=integrity.getString(key(track.id),"");file=new File(path);lease=files.acquire(file);}
            boolean valid=lease!=null&&file.length()>0;
            try{if(valid&&!record.isEmpty()){String[] parts=record.split(":",2);try{valid=parts.length==2&&file.length()==Long.parseLong(parts[0])&&DownloadCopy.digest(file).equals(parts[1]);}catch(NumberFormatException e){valid=false;}}}
            finally{if(lease!=null)lease.close();}
            if(!valid)synchronized(this){
                if(!path.equals(library.offline(track.id))||!record.equals(integrity.getString(key(track.id),"")))continue;
                if(files.inUse(file)){retained++;continue;}
                if(files.owns(file)&&file.exists()&&!files.delete(file)){retained++;continue;}
                library.offline(track.id,"");integrity.edit().remove(key(track.id)).apply();damaged.add(track);
            }
        }return new Verification(List.copyOf(damaged),retained);
    }
    public synchronized long cleanup(Set<String> activeIds){
        Set<String> keep=new HashSet<>();for(Track track:library.offlineTracks())keep.add(new File(library.offline(track.id)).getName());
        for(String id:activeIds){keep.add(partial(id).getName());keep.add(target(id).getName());}
        long removed=0;File[] entries=directory.listFiles();if(entries!=null)for(File file:entries)if(file.getName().matches("[a-f0-9]{64}\\.(part|audio)")&&!keep.contains(file.getName())){long size=file.length();if(files.delete(file))removed+=size;}
        return removed;
    }
}
