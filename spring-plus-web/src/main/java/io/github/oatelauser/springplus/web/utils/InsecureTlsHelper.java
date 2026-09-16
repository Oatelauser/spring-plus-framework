package io.github.oatelauser.springplus.web.utils;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

/**
 * TLS工具类
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-03-03
 * @since 1.0
 */
public final class InsecureTlsHelper {

    private InsecureTlsHelper() {}

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] c, String a) {}

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {return new X509Certificate[0];}
    };

    private static final HostnameVerifier ALLOW_ALL = (hostname, session) -> true;

    public static SSLContext trustAllContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{ TRUST_ALL }, null);
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to create trust-all SSLContext", e);
        }
    }

    /**
     * SIMPLE 引擎（HttpURLConnection）需要的 SSLSocketFactory
     */
    public static SSLSocketFactory trustAllSocketFactory() {
        return trustAllContext().getSocketFactory();
    }

    public static X509TrustManager trustAllManager() {
        return TRUST_ALL;
    }

    public static HostnameVerifier allowAllVerifier() {
        return ALLOW_ALL;
    }

}
