package io.github.tontu89.debugserverlib.utils;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import io.github.tontu89.debugserverlib.model.HttpResponseInfo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Dispatcher;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.web.client.RestTemplate;


import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;


@Slf4j
public class HttpUtils {

    private static Pattern HTTP_URL_CONNECTION_EXCEPTION = Pattern.compile("Server returned HTTP response code: (\\d+).*");

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

    public static HttpResponseInfo executeHttpRequestByRest(String host, String uri, String httpMethod, Map<String, String> headers, String payload) {
        HttpResponseInfo responseInfo = null;
        try {
            host = host.endsWith("/") ? (host.substring(0, host.length() - 1)) : host;
            uri = StringUtils.isBlank(uri) ? "" : (uri.startsWith("/") ? uri : ("/" + uri));
            URL url = new URL(host + uri);

            RestTemplate restTemplate = prepareRestTemplate(new RestTemplateBuilder());

            responseInfo = restTemplate.execute(url.toString(), HttpMethod.resolve(httpMethod), (RequestCallback) request -> {
                AtomicBoolean needToResetContentLength = new AtomicBoolean(false);

                headers.forEach((k, v) -> {
                    if (!"content-length".equalsIgnoreCase(k)) {
                        request.getHeaders().add(k, v);
                    } else {
                        needToResetContentLength.set(true);
                    }
                });

                if (payload != null && payload.length() > 0) {
                    byte[] data = payload.getBytes(StandardCharsets.UTF_8);

                    if (needToResetContentLength.get()) {
                        request.getHeaders().add("content-length", data.length + "");
                    }

                    OutputStream outputStream = request.getBody();
                    outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            }, (ResponseExtractor<HttpResponseInfo>) response -> {
                String returnedPayload = null;
                if (response.getBody() != null) {
                    byte[] data = response.getBody().readAllBytes();
                    if (data != null && data.length > 0) {
                        returnedPayload = new String(data);
                    }
                }
                HttpResponseInfo restResponse = HttpResponseInfo.builder()
                        .httpStatus(response.getRawStatusCode())
                        .headers(fromHttpHeadersToMap(response.getHeaders()))
                        .payload(returnedPayload)
                        .build();
                restResponse.removeEncodingHeader();
                return restResponse;
            });
        } catch (HttpClientErrorException e) {
            responseInfo = HttpResponseInfo.builder()
                    .httpStatus(e.getRawStatusCode())
                    .payload(e.getResponseBodyAsString())
                    .build();
        } catch (Throwable e) {
            log.info(Constants.LOG_ERROR_PREFIX + e.getMessage(), e);

            responseInfo = HttpResponseInfo.builder()
                    .httpStatus(500)
                    .payload(e.getMessage())
                    .build();
        }

        log.debug("DebugLib: Request URI [{}] | method [{}] | headers [{}] | payload [{}] | with status [{}] and result [{}]", host + uri, httpMethod, headers, payload, responseInfo.getHttpStatus(), responseInfo);

        return responseInfo;
    }

    @SneakyThrows
    public static HttpResponseInfo executeHttpRequest(String host, String uri, String httpMethod, Map<String, String> headers, String payload) {
        HttpResponseInfo responseInfo = null;
        try {
            host = host.endsWith("/") ? (host.substring(0, host.length() - 1)) : host;
            uri = StringUtils.isBlank(uri) ? "" : (uri.startsWith("/") ? uri : ("/" + uri));
            URL url = new URL(host + uri);

            OkHttpClient client = prepareOkHttpClient();

            RequestBody requestBody = null;

            if (payload != null && payload.length() > 0) {
                requestBody = RequestBody.create(null, payload.getBytes(StandardCharsets.UTF_8));
            }

            Request request = new Request.Builder()
                    .method(httpMethod, requestBody)
                    .url(url)
                    .headers(fromMapToHeaders(headers))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                responseInfo = HttpResponseInfo.builder()
                        .httpStatus(response.code())
                        .headers(fromHeadersToMap(request.headers()))
                        .payload(response.body().string())
                        .build();
                return responseInfo;
            }
        } catch (Throwable e) {
            log.info(Constants.LOG_ERROR_PREFIX + e.getMessage(), e);
        }
        if (responseInfo == null) {
            responseInfo = HttpResponseInfo.builder()
                    .httpStatus(500)
                    .build();
        }

        log.debug("DebugLib: Request URI [{}] | method [{}] | headers [{}] | payload [{}] | with status [{}] and result [{}]", host + uri, httpMethod, headers, payload, responseInfo.getHttpStatus(), responseInfo);

        return responseInfo;
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
        for (String name : headers.names()) {
            result.put(name, headers.get(name));
        }
        return result;
    }

    private static OkHttpClient prepareOkHttpClient() {
        Dispatcher dispatcher = new Dispatcher();

        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(msg -> log.debug("DebugLib: {}", msg));
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        Config config = new ConfigBuilder().build();
        OkHttpClient httpClient = HttpClientUtils.createHttpClient(config);

        log.debug("DebugLib: httpclient before: interceptor [{}]", httpClient.interceptors() == null ? 0 : httpClient.interceptors().size());
        OkHttpClient httpClientNew = httpClient.newBuilder()
                .addNetworkInterceptor(loggingInterceptor).build();

        log.debug("DebugLib: httpclient after: interceptor [{}]", httpClient.interceptors() == null ? 0 : httpClient.interceptors().size());

        return httpClientNew;
    }

    private static Headers fromMapToHeaders(Map<String, String> headers) {
        Headers.Builder result = new Headers.Builder();
        if (headers != null) {
            for (String name : headers.keySet()) {
                if (!"content-length".equalsIgnoreCase(name)) {
                    result.add(name, headers.get(name));
                }
            }
        }
        return result.build();
    }

    private static RestTemplate prepareRestTemplate(RestTemplateBuilder builder) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        /*
         * Ignore untrusted certificates
         */
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        // Install the all-trusting trust manager
        SSLContext sslContext = SSLContext.getInstance("SSL");

        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        /*
         * Create an HttpClient that uses the custom SSLContext and do not verify cert hostname
         */
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();


        HttpComponentsClientHttpRequestFactory customRequestFactory =
                new HttpComponentsClientHttpRequestFactory();

        customRequestFactory.setHttpClient(httpClient);

        /*
         * Create a RestTemplate that uses custom request factory
         */
        return builder.requestFactory(() -> customRequestFactory).build();

    }

    private static Map<String, String> fromHttpHeadersToMap(HttpHeaders headers) {
        Map<String, String> result = new HashMap<>();
        Optional.ofNullable(headers).ifPresent(httpHeaders -> httpHeaders.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                result.put(key, values.get(values.size() - 1));
            }
        }));

        return result;
    }
}
