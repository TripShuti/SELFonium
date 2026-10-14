package app.selfonium.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Remap a stored index after removals; a replacement track starts at zero. */
public record QueueRestore(List<String> ids,int index,long position) {
    public QueueRestore {ids=List.copyOf(ids);}
    public static QueueRestore resolve(List<String> saved,int previous,long position,Predicate<String> available){
        List<String> ids=new ArrayList<>();int before=0;boolean selected=false;
        int chosen=Math.max(0,Math.min(previous,saved.size()-1));
        for(int at=0;at<saved.size();at++)if(available.test(saved.get(at))){
            if(at<chosen)before++;if(at==chosen)selected=true;ids.add(saved.get(at));
        }
        int index=Math.min(before,Math.max(0,ids.size()-1));
        return new QueueRestore(ids,index,selected&&previous==chosen?Math.max(0,position):0);
    }
}
