package app.selfonium.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Direct children of a private directory only. Leases prevent deleting files in use. */
public final class ManagedFiles {
    private final File directory;
    private final Map<String,Integer> readers=new HashMap<>();
    public ManagedFiles(File directory){this.directory=directory;}
    public boolean owns(File file){
        try{return !Files.isSymbolicLink(directory.toPath())&&!Files.isSymbolicLink(file.toPath())&&file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile());}
        catch(IOException error){return false;}
    }
    public synchronized Lease acquire(File file){
        if(!owns(file)||!file.isFile())return null;String key;try{key=file.getCanonicalPath();}catch(IOException error){return null;}readers.merge(key,1,Integer::sum);return new Lease(key);
    }
    public synchronized boolean inUse(File file){try{return readers.containsKey(file.getCanonicalPath());}catch(IOException error){return true;}}
    public synchronized boolean delete(File file){return owns(file)&&!inUse(file)&&(!file.exists()||file.isFile()&&file.delete());}
    public synchronized boolean publish(File partial,File target)throws IOException{
        if(!owns(partial)||!owns(target)||inUse(target)||!partial.isFile())return false;
        try{Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException error){Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}return true;
    }
    public final class Lease implements AutoCloseable{
        private String key;private Lease(String key){this.key=key;}
        @Override public void close(){synchronized(ManagedFiles.this){if(key!=null){int count=readers.get(key);if(count==1)readers.remove(key);else readers.put(key,count-1);key=null;}}}
    }
    /** Do not follow symbolic links, including links created inside a cache tree. */
    public static long bytes(File root){
        if(Files.isSymbolicLink(root.toPath()))return 0;if(root.isFile())return root.length();
        File[] children=root.listFiles();long sum=0;if(children!=null)for(File child:children)sum=saturatedAdd(sum,bytes(child));return sum;
    }
    public static long saturatedAdd(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
}
