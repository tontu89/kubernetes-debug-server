package io.github.tontu89.debugserverlib.model;

import com.auth0.jwt.exceptions.JWTDecodeException;
import io.github.tontu89.debugserverlib.utils.HttpUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static io.github.tontu89.debugserverlib.utils.Constants.LOG_ERROR_PREFIX;

@Slf4j
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class HttpRequestInfo {
    private String uri;
    private String method;
    private Map<String, String> headers;
    private String payload;
    private Map authorization;

    public static HttpRequestInfo fromHttpRequest(HttpServletRequest request, boolean withPayload, boolean extractAuth) {
        Map authorization = null;
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName, headerValue);

            if (extractAuth && "authorization".equalsIgnoreCase(headerName.toLowerCase(Locale.ROOT)) && !StringUtils.isBlank(headerValue)) {
                try {
                    if (headerValue.startsWith("Bearer")) {
                        authorization = HttpUtils.decodeJwtPayload(headerValue.substring("Bearer ".length()));
                    } else {
                        authorization = HttpUtils.decodeJwtPayload(headerValue);
                    }
                } catch (JWTDecodeException e){
                    log.error("DebugLib: failed to parse authorization jwt token [{}]", headerValue);
                    log.error(LOG_ERROR_PREFIX + e.getMessage(), e);
                }
            }
        }

        String payload = null;
        if (withPayload) {
            payload = HttpUtils.getBody(request);
        }

        HttpRequestInfo originalRequestInfo = HttpRequestInfo.builder()
                .uri(request.getRequestURI() + (StringUtils.isBlank(request.getQueryString()) ? "" : ("?" + request.getQueryString())))
                .method(request.getMethod())
                .headers(headers)
                .payload(payload)
                .authorization(authorization)
                .build();

        return originalRequestInfo;
    }
}
