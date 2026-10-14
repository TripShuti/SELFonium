package app.selfonium.data;

import app.selfonium.locale.Texts;
import app.selfonium.R;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import app.selfonium.core.Track;
import java.util.ArrayList;
import java.util.List;

public final class LocalMusic {
    public static List<Track> scan(Context context) {
        List<Track> result=new ArrayList<>();
        String[] columns={"_id","title","artist","album","duration","track"};
        try(Cursor c=context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,columns,null,null,"title COLLATE NOCASE")){
            if(c==null)throw new IllegalStateException(Texts.get(context,R.string.ui_media_library_unavailable));
            while(c.moveToNext()) {
                String id=c.getString(0);String uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,c.getLong(0)).toString();
                result.add(new Track("device:"+id,"device",id,c.getString(1),c.getString(2),c.getString(3),uri,c.getLong(4),c.getInt(5)%1000));
            }
        }
        return result;
    }
    public static Track importUri(Context context,Uri uri) {
        String name="Аудіофайл",artist="",album="";long duration=0;
        try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
        MediaMetadataRetriever reader=new MediaMetadataRetriever();
        try{
            reader.setDataSource(context,uri);
            String title=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);if(title!=null&&!title.trim().isEmpty())name=title;
            artist=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);album=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String value=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(value!=null)duration=Long.parseLong(value);
        }catch(Exception ignored){}finally{try{reader.release();}catch(Exception ignored){}}
        return new Track("file:"+uri,"files",uri.toString(),name,artist,album,uri.toString(),duration,0);
    }
}
