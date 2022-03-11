package io.github.tontu89.debugserverlib.utils;

import io.github.tontu89.debugserverlib.model.HttpResponseInfo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Dispatcher;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;


@Slf4j
public class HttpUtils {

    private static Pattern HTTP_URL_CONNECTION_EXCEPTION = Pattern.compile("Server returned HTTP response code: (\\d+).*");
    private static OkHttpClient httpClient;

    @SneakyThrows
    public static String getBody(HttpServletRequest request) {

        String body = null;
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = null;

        try {
            InputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                char[] charBuffer = new char[128];
                int bytesRead = -1;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            } else {
                stringBuilder.append("");
            }
        } catch (IOException e) {
            log.error(Constants.LOG_ERROR_PREFIX + e.getMessage(), e);
            throw e;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    log.error(Constants.LOG_ERROR_PREFIX + e.getMessage(), e);
                    throw e;
                }
            }
        }

        body = stringBuilder.toString();
        return body;
    }

    @SneakyThrows
    public static HttpResponseInfo executeHttpRequest(String host, String uri, String httpMethod, Map<String, String> headers, String payload, boolean forceNoProxy, boolean forceIgnoreSsl) {
        try {
            host = host.endsWith("/") ? (host.substring(0, host.length() - 1)) : host;
            uri = StringUtils.isBlank(uri) ? "" : (uri.startsWith("/") ? uri : ("/" + uri));
            URL url = new URL(host + uri);

            OkHttpClient client = prepareOkHttpClient(forceNoProxy, forceIgnoreSsl);

            RequestBody requestBody = null;

            if (payload != null && payload.length() > 0) {
                requestBody = RequestBody.create(payload.getBytes(StandardCharsets.UTF_8));
            }

            Request request = new Request.Builder()
                    .method(httpMethod, requestBody)
                    .url(url)
                    .headers(fromMapToHeaders(headers))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                HttpResponseInfo responseInfo = HttpResponseInfo.builder()
                        .httpStatus(response.code())
                        .headers(fromHeadersToMap(request.headers()))
                        .payload(response.body().string())
                        .build();

                return responseInfo;
            }
        } catch (Throwable e) {
            log.info(Constants.LOG_ERROR_PREFIX + e.getMessage(), e);
        }
        return HttpResponseInfo.builder()
                .httpStatus(500)
                .build();
    }

    public static Map<String, String> getHttpResponseHeader(HttpURLConnection connection) {
        Map<String, String> result = new HashMap<>();

        connection.getHeaderFields().forEach((name, values) -> {
            if (name != null) {
                result.put(name, StringUtils.join(values, ";"));
            }
        });

        return result;
    }

    @SneakyThrows
    private static InputStream prepareInputStream(String encoding, InputStream inputStream) {
        if ("gzip".equalsIgnoreCase(encoding)) {
            return new GZIPInputStream(inputStream);
        } else {
            return inputStream;
        }
    }

    @SneakyThrows
    public static Map decodeJwtPayload(String jwtString) {
        String[] jwtParts = jwtString.split("\\.");

        if (jwtParts.length >= 2) {
            String payload = jwtParts[1];

            byte[] payloadInBytes = Base64.getDecoder().decode(payload);

            return Constants.OBJECT_MAPPER.readValue(payloadInBytes, HashMap.class);
        }
        return null;
    }

    private static Map<String, String> fromHeadersToMap(Headers headers) {
        Map<String, String> result = new HashMap<>();
        for(String name : headers.names()) {
            result.put(name, headers.get(name));
        }
        return result;
    }

    private static OkHttpClient prepareOkHttpClient(boolean forceNoProxy, boolean forceIgnoreSsl) throws NoSuchAlgorithmException, KeyManagementException {
        Dispatcher dispatcher = new Dispatcher();

        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(msg -> log.debug("DebugLib: {}", msg));
        loggingInterceptor.level(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.MINUTES)
                .readTimeout(1, TimeUnit.MINUTES)
                .dispatcher(dispatcher)
                .addNetworkInterceptor(loggingInterceptor);

        if (forceNoProxy) {
            builder.proxy(Proxy.NO_PROXY);
        }

        if (forceIgnoreSsl) {
            TrustManager[] trustAllCerts = new TrustManager[]{
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

            SSLContext trustAllSslContext = null;
            trustAllSslContext = SSLContext.getInstance("SSL");
            trustAllSslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory trustAllSslSocketFactory = trustAllSslContext.getSocketFactory();

            builder.sslSocketFactory(trustAllSslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
        }

        return builder.build();
    }

    private static Headers fromMapToHeaders(Map<String, String> headers) {
        Headers.Builder result = new Headers.Builder();
        if (headers != null) {
            for(String name : headers.keySet()) {
                if (!"content-length".equalsIgnoreCase(name)) {
                    result.add(name, headers.get(name));
                }
            }
        }
        return result.build();
    }
}
