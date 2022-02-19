package com.kubernetes.debugserver.model;

import com.kubernetes.debugserver.utils.HttpUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class HttpRequestInfo {
    private String uri;
    private String method;
    private Map<String, String> headers;
    private String payload;

    public static HttpRequestInfo fromHttpRequest(HttpServletRequest request, boolean withPayload) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));

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
                .build();

        return originalRequestInfo;
    }
}
