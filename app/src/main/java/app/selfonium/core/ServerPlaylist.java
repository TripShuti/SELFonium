package app.selfonium.core;
import java.util.List;
/** Server identity is scoped by source; entries keep server order, including repeats. */
public record ServerPlaylist(String id,String name,String owner,List<Track> tracks) {
    public ServerPlaylist { tracks=List.copyOf(tracks); }
}
