package app.selfonium.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import app.selfonium.core.Source;
import org.json.JSONArray;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials stay in private storage encrypted with a non-exportable Android Keystore key. */
public final class SourceStore {
    private final SharedPreferences prefs;
    private List<Source> cached;
    // Retain retired/unknown entries verbatim inside the encrypted vault on future saves.
    private SourceRecords records;
    private static Source copy(Source source){return new Source(source.id,source.name,source.type,source.baseUrl,source.username,source.password);}
    private List<Source> snapshot(){List<Source> result=new ArrayList<>();for(Source s:cached)result.add(copy(s));return result;}
    private static final String ALIAS="selfonium.sources.v1";
    public SourceStore(Context context) {prefs=context.getSharedPreferences("sources",Context.MODE_PRIVATE);}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    public synchronized List<Source> all() {
        if(cached!=null)return snapshot();
        String encrypted=prefs.getString("vault","");List<Source> result=new ArrayList<>();
        try {
            if(encrypted.isEmpty()){records=new SourceRecords(new JSONArray());cached=result;return snapshot();}
            String[] parts=encrypted.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
            JSONArray array=new JSONArray(new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));
            records=new SourceRecords(array);result.addAll(records.supported);
            cached=result;return snapshot();
        }catch(Exception e){throw new IllegalStateException("Не вдалося відкрити сховище джерел. Дані не перезаписано.");}
    }
    public synchronized Source get(String id) {for(Source s:all())if(s.id.equals(id))return s;return null;}
    public synchronized void save(Source source) {List<Source> values=all();values.removeIf(s->s.id.equals(source.id));values.add(source);write(values);}
    public synchronized void remove(String id) {List<Source> values=all();values.removeIf(s->s.id.equals(id));write(values);}
    private void write(List<Source> values) {
        try {
            JSONArray array=records.encode(values);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
            String payload=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(array.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
            if(!prefs.edit().putString("vault",payload).commit())throw new IllegalStateException("Storage unavailable");
            cached=new ArrayList<>();for(Source source:values)cached.add(copy(source));
        }catch(Exception e){throw new IllegalStateException("Не вдалося зберегти джерела");}
    }
}
