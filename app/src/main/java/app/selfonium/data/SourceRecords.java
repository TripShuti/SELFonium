package app.selfonium.data;

import app.selfonium.core.Source;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Vault serialization independent of Android encryption. Unknown types remain opaque. */
public final class SourceRecords {
    public final List<Source> supported=new ArrayList<>();
    private final JSONArray archived=new JSONArray();
    public SourceRecords(JSONArray records) throws Exception {
        for(int i=0;i<records.length();i++){
            JSONObject record=records.getJSONObject(i);
            if(!"SUBSONIC".equals(record.optString("type"))){archived.put(record);continue;}
            supported.add(new Source(record.getString("id"),record.getString("name"),Source.Type.SUBSONIC,record.getString("url"),record.optString("user"),record.optString("password")));
        }
    }
    public JSONArray encode(List<Source> sources) throws Exception {
        JSONArray records=new JSONArray();
        for(int i=0;i<archived.length();i++)records.put(archived.getJSONObject(i));
        for(Source source:sources)records.put(new JSONObject().put("id",source.id).put("name",source.name).put("type","SUBSONIC").put("url",source.baseUrl).put("user",source.username).put("password",source.password));
        return records;
    }
}
