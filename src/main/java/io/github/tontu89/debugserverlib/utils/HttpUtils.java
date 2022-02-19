package io.github.tontu89.debugserverlib.utils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;


@Slf4j
public class HttpUtils {

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
            log.error(Constants.LOG_ERROR_PREFIX, e);
            throw e;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    log.error(Constants.LOG_ERROR_PREFIX, e);
                    throw e;
                }
            }
        }

        body = stringBuilder.toString();
        return body;
    }

    @SneakyThrows
    public static String executeHttpRequest(String host, String uri, String httpMethod, Map<String, String> headers, String payload, Consumer<HttpURLConnection> action) {
        host = host.endsWith("/") ? (host.substring(0, host.length() - 1)) : host;
        uri = StringUtils.isBlank(uri) ? "" : (uri.startsWith("/") ? uri : ("/" + uri));
        try {
            URL url = new URL(host + uri);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            connection.setRequestMethod(httpMethod);
            headers.forEach((name, value) -> connection.setRequestProperty(name, value));

            if (payload != null && payload.length() > 0) {
                connection.setDoOutput(true);
                DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
                writer.write(payload.getBytes(StandardCharsets.UTF_8));
                writer.flush();
                writer.close();
            }

            BufferedReader br = null;
            if (connection.getErrorStream() != null) {
                br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
            } else {
                br = new BufferedReader(new InputStreamReader(prepareInputStream(connection.getContentEncoding(), connection.getInputStream()), "UTF-8"));
            }

            String responseString = null;
            String str;
            StringBuffer responseData = new StringBuffer();
            while ((str = br.readLine()) != null) {
                responseData.append(str).append("\n");
            }

            br.close();
            connection.disconnect();

            if (responseData.length() > 0) {
                responseString = responseData.substring(0, responseData.length() - 1);
            }

            if (action != null) {
                action.accept(connection);
            }
            return responseString;
        } catch (Exception e) {
            log.info("DebugLib: exception" + e.getMessage(), e);

            HttpURLConnection httpURLConnection = new HttpURLConnection(new URL(host + uri)) {
                @Override
                public void connect() throws IOException {

                }

                @Override
                public void disconnect() {

                }

                @Override
                public boolean usingProxy() {
                    return false;
                }

                @Override
                public String getRequestMethod() {
                    return httpMethod;
                }

                @Override
                public int getResponseCode() throws IOException {
                    return 500;
                }

                @Override
                public String getResponseMessage() throws IOException {
                    return "Connection refused";
                }

                @Override
                public Map<String, List<String>> getHeaderFields() {
                    return new HashMap<>();
                }
            };

            action.accept(httpURLConnection);

            return "Connection refused to " + host + uri;
        }
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
}
