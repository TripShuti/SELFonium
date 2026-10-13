package app.selfonium.net;

import android.os.Bundle;
import android.test.InstrumentationTestCase;
import java.lang.reflect.Field;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;

/** Checks the real HTTP client's trust anchors in the target app's Android process. */
@SuppressWarnings("deprecation")
public final class InstalledCaTrustTest extends InstrumentationTestCase {
    public void testInstalledAuthoritiesAreTrustedByHttpClient() throws Exception {
        Field field = Http.class.getDeclaredField("CLIENT");
        field.setAccessible(true);
        OkHttpClient client = (OkHttpClient) field.get(null);
        X509TrustManager manager = client.x509TrustManager();
        assertNotNull("HTTP client must use certificate validation", manager);
        Set<Certificate> trusted = new HashSet<>(Arrays.asList(manager.getAcceptedIssuers()));
        KeyStore store = KeyStore.getInstance("AndroidCAStore");
        store.load(null);
        int system = 0;
        int user = 0;
        for (Enumeration<String> aliases = store.aliases(); aliases.hasMoreElements();) {
            String alias = aliases.nextElement();
            if (!alias.startsWith("system:") && !alias.startsWith("user:")) continue;
            assertTrue("HTTP client does not trust an installed "
                    + (alias.startsWith("user:") ? "user" : "system") + " CA",
                    trusted.contains(store.getCertificate(alias)));
            if (alias.startsWith("user:")) user++; else system++;
        }
        assertTrue("Device must expose system trust anchors", system > 0);
        Bundle status = new Bundle();
        status.putString("stream", "Verified HTTP trust anchors: system=" + system + ", user=" + user + "\n");
        getInstrumentation().sendStatus(0, status);
    }
}
