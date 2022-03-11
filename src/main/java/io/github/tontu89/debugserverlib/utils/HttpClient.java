package io.github.tontu89.debugserverlib.utils;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Credentials;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.Interceptor.Chain;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

public class HttpClient {


    ;
    // {{start:logging}}
    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

    private static final HttpLoggingInterceptor loggingInterceptor =
            new HttpLoggingInterceptor((msg) -> {
                log.debug(msg);
            });

    static {
//        if (log.isDebugEnabled()) {
//            loggingInterceptor.level(Level.BASIC);
//        } else if (log.isTraceEnabled()) {
        loggingInterceptor.level(Level.BODY);
//        }
    }

    public static HttpLoggingInterceptor getLoggingInterceptor() {
        return loggingInterceptor;
    }
    // {{end:logging}}

    public static Interceptor getHeaderInterceptor(String name, String value) {
        return (Chain chain) -> {
            Request orig = chain.request();
            Request newRequest = orig.newBuilder().addHeader(name, value).build();
            return chain.proceed(newRequest);
        };
    }

    public static Interceptor basicAuth(String user, String password) {
        return (Chain chain) -> {
            Request orig = chain.request();
            String credential = Credentials.basic(user, password);
            Request newRequest = orig.newBuilder().addHeader("Authorization", credential).build();
            return chain.proceed(newRequest);
        };
    }

    private static final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
    };

    // {{start:client}}
    private static OkHttpClient client = null;
    private static SSLContext trustAllSslContext = null;

    static {
        try {
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.setMaxRequestsPerHost(15);

            Builder builder = new Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .writeTimeout(1, TimeUnit.MINUTES)
                    .readTimeout(1, TimeUnit.MINUTES)
                    .dispatcher(dispatcher)
                    .addNetworkInterceptor(loggingInterceptor)
                    .proxy(Proxy.NO_PROXY);

            trustAllSslContext = SSLContext.getInstance("SSL");
            trustAllSslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory trustAllSslSocketFactory = trustAllSslContext.getSocketFactory();

            builder.sslSocketFactory(trustAllSslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            client = builder.build();
        } catch (Throwable e) {
            log.error(Constants.LOG_ERROR_PREFIX + e.getMessage(), e);
        }
    }

    ;

    /*
     * Global client that can be shared for common HTTP tasks.
     */
    public static OkHttpClient globalClient() {
        return client;
    }
    // {{end:client}}

    // {{start:cookieJar}}
    /*
     * Creates a new client from the global client with
     * a stateful cookie jar. This is useful when you need
     * to access password protected sites.
     */
    public static OkHttpClient newClientWithCookieJar() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        JavaNetCookieJar cookieJar = new JavaNetCookieJar(cookieManager);
        return client.newBuilder().cookieJar(cookieJar).build();
    }
    // {{end:cookieJar}}

    public static RuntimeException unknownException(Response response) throws IOException {
        return new RuntimeException(String.format("code: %s, body: %s", response.code(), response.body().string()));
    }
}