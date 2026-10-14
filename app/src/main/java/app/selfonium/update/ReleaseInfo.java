package app.selfonium.update;

import java.math.BigDecimal;
import java.net.URI;
import org.json.*;
import static app.selfonium.update.UpdatePolicy.*;

/** Strictly binds GitHub assets to the existing signed-release manifest. */
public final class ReleaseInfo {
    public static final String REPO="https://github.com/TripShuti/SELFonium/releases/download/";
    public final String version,sha256,signer,notes,published,apkUrl,manifestUrl;
    public final long code,size;
    public final String releaseJson,manifestJson;
    private ReleaseInfo(Assets a,JSONObject manifest,String raw)throws Failure{
        try{
            version=string(manifest,"version");code=integer(manifest,"versionCode");
            sha256=string(manifest,"sha256").toLowerCase(java.util.Locale.ROOT);signer=string(manifest,"signerSha256").toLowerCase(java.util.Locale.ROOT);
            if(!PACKAGE.equals(string(manifest,"package"))||!version.equals(a.version)||code<1||code>Integer.MAX_VALUE||!sha256.matches("[a-f0-9]{64}")||!signer.matches("[a-f0-9]{64}")||!Boolean.FALSE.equals(manifest.get("debuggable"))||!Boolean.FALSE.equals(manifest.get("workingTreeDirty")))throw new IllegalArgumentException();
            notes=a.notes;published=a.published;size=a.size;apkUrl=a.apkUrl;manifestUrl=a.manifestUrl;releaseJson=a.raw;manifestJson=raw;
        }catch(Exception e){throw new Failure(Reason.INVALID_RELEASE);}
    }
    static String string(JSONObject json,String name)throws JSONException{Object value=json.get(name);if(!(value instanceof String))throw new JSONException("Expected string");return (String)value;}
    static long integer(JSONObject json,String name)throws JSONException{Object value=json.get(name);if(!(value instanceof Number))throw new JSONException("Expected integer");try{return new BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException e){throw new JSONException("Expected integer");}}
    public static final class Assets {
        final String version,notes,published,apkUrl,manifestUrl,raw;final long size;
        Assets(String version,String notes,String published,String apkUrl,String manifestUrl,long size,String raw){this.version=version;this.notes=notes;this.published=published;this.apkUrl=apkUrl;this.manifestUrl=manifestUrl;this.size=size;this.raw=raw;}
        public ReleaseInfo manifest(String json)throws Failure{try{return new ReleaseInfo(this,new JSONObject(json),json);}catch(JSONException e){throw new Failure(Reason.INVALID_RELEASE);}}
    }
    public static Assets assets(String raw)throws Failure{
        try{
            JSONObject json=new JSONObject(raw);
            if(!(json.get("draft") instanceof Boolean)||!(json.get("prerelease") instanceof Boolean))throw new IllegalArgumentException();
            if(json.getBoolean("draft")||json.getBoolean("prerelease"))return null;
            String tag=string(json,"tag_name");if(!tag.matches("v(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)"))throw new IllegalArgumentException();
            String version=tag.substring(1),published=string(json,"published_at");java.time.Instant.parse(published);
            Object body=json.opt("body");String notes=body==null||body==JSONObject.NULL?"":string(json,"body");if(notes.length()>65_536)throw new IllegalArgumentException();
            JSONArray list=json.getJSONArray("assets");JSONObject apk=null,manifest=null;String stem=null;
            for(int i=0;i<list.length();i++){
                JSONObject asset=list.getJSONObject(i);String name=string(asset,"name");
                if(name.equals("SELFonium-v"+version+".apk")||name.equals("SELFonium-"+version+".apk")){
                    if(apk!=null)throw new IllegalArgumentException();apk=asset;stem=name.substring(0,name.length()-4);
                }
            }
            if(apk==null)throw new Failure(Reason.NO_ASSET);
            for(int i=0;i<list.length();i++){JSONObject asset=list.getJSONObject(i);if(string(asset,"name").equals(stem+".json")){if(manifest!=null)throw new IllegalArgumentException();manifest=asset;}}
            if(manifest==null)throw new Failure(Reason.NO_ASSET);
            long size=integer(apk,"size"),manifestSize=integer(manifest,"size");
            if(size<=0||size>MAX_APK||manifestSize<=0||manifestSize>16_384)throw new IllegalArgumentException();
            String apkUrl=assetUrl(apk,tag),manifestUrl=assetUrl(manifest,tag);
            return new Assets(version,notes,published,apkUrl,manifestUrl,size,raw);
        }catch(Failure e){throw e;}catch(Exception e){throw new Failure(Reason.INVALID_RELEASE);}
    }
    private static String assetUrl(JSONObject asset,String tag)throws Exception{
        String name=string(asset,"name"),url=string(asset,"browser_download_url");
        if(!"uploaded".equals(string(asset,"state"))||!url.equals(REPO+tag+"/"+name))throw new IllegalArgumentException();
        URI parsed=new URI(url);if(parsed.getRawUserInfo()!=null||parsed.getRawQuery()!=null||parsed.getRawFragment()!=null)throw new IllegalArgumentException();return url;
    }
    public static ReleaseInfo restore(String release,String manifest)throws Failure{Assets a=assets(release);if(a==null)throw new Failure(Reason.INVALID_RELEASE);return a.manifest(manifest);}
}
