package io.github.tontu89.debugserverlib.model;

import com.auth0.jwt.exceptions.JWTDecodeException;
import io.github.tontu89.debugserverlib.filter.requestwrapper.CachedBodyHttpServletRequest;
import io.github.tontu89.debugserverlib.utils.HttpUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
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
public class HttpRequestInfo implements Serializable {
    private String uri;
    private String method;
    private Map<String, String> headers;
    private String payload;

    public static HttpRequestInfo fromHttpRequest(CachedBodyHttpServletRequest request, boolean withPayload) {
        Map<String, String> headers = request.getHeaders();

        String payload = null;
        if (withPayload) {
            payload = HttpUtils.getBody(request);
        }

        HttpRequestInfo originalRequestInfo = HttpRequestInfo.builder()
                .uri(request.getRequestURI() + (StringUtils.isBlank(request.getQueryString()) ? "" : ("?" + request.getQueryString())))
                .method(request.getMethod())
                .headers(headers)
                .payload(payload)
                .build();

        return originalRequestInfo;
    }
}
