package com.kubernetes.debugserver.filter;

import com.kubernetes.debugserver.ClientHandler;
import com.kubernetes.debugserver.filter.requestwrapper.CachedBodyHttpServletRequest;
import com.kubernetes.debugserver.model.HttpResponseInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


@Component
@Slf4j
public class DebugSpringFilter implements Filter {

    private final List<ClientHandler> debugClientHandlers = new ArrayList<>();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        boolean matched = false;

        CachedBodyHttpServletRequest cachedBodyHttpServletRequest = new CachedBodyHttpServletRequest((HttpServletRequest) servletRequest);
        HttpServletResponse res = (HttpServletResponse) servletResponse;

        try {
            for (int i = 0 ; i < this.debugClientHandlers.size(); ++i) {
                ClientHandler debugClientHandler = this.debugClientHandlers.get(i);

                if (debugClientHandler.getStatus() == ClientHandler.Status.STOPPED) {
                    this.debugClientHandlers.remove(i);
                    i--;
                } else if (debugClientHandler.isMatch(cachedBodyHttpServletRequest)) {
                    matched = true;
                    HttpResponseInfo clientResponse = debugClientHandler.forwardRequestToClient(cachedBodyHttpServletRequest);

                    for(String header : clientResponse.getHeaders().keySet()) {
                        // Content is plain text
                        if ("Content-Encoding".equalsIgnoreCase(header) && "gzip".equalsIgnoreCase(clientResponse.getHeaders().get(header))) {
                            continue;
                        } else if ("transfer-encoding".equalsIgnoreCase(header)) {
                            continue;
                        } else {
                            res.addHeader(header, clientResponse.getHeaders().get(header));
                        }
                    }

                    res.setStatus(clientResponse.getHttpStatus());
                    byte[] responseData = clientResponse.getPayload().getBytes(StandardCharsets.UTF_8);
                    res.setContentLength(responseData.length);
                    res.getOutputStream().write(responseData);
                    break;
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }

        if (!matched) {
            filterChain.doFilter(cachedBodyHttpServletRequest, servletResponse);
        }
    }

    public void addDebugClient(ClientHandler debugClientHandler) {
        this.debugClientHandlers.add(debugClientHandler);
    }
}
