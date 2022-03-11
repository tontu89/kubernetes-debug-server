package io.github.tontu89.debugserverlib.filter;

import io.github.tontu89.debugserverlib.ClientHandler;
import io.github.tontu89.debugserverlib.filter.requestwrapper.CachedBodyHttpServletRequest;
import io.github.tontu89.debugserverlib.model.HttpResponseInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static io.github.tontu89.debugserverlib.utils.Constants.LOG_ERROR_PREFIX;


@Profile("RemoteDebug")
@Component
@Slf4j
public class DebugServerSpringFilter implements Filter {
    private final Executor executor = Executors.newCachedThreadPool();
    private final CopyOnWriteArrayList<ClientHandler> debugClientHandlers = new CopyOnWriteArrayList<>();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        boolean matched = false;

        CachedBodyHttpServletRequest cachedBodyHttpServletRequest = new CachedBodyHttpServletRequest((HttpServletRequest) servletRequest);
        HttpServletResponse res = (HttpServletResponse) servletResponse;

        log.info("DebugLib: Check matching request for {}", cachedBodyHttpServletRequest.getRequestURI());

        try {
            for (int i = 0 ; i < this.debugClientHandlers.size(); ++i) {
                ClientHandler debugClientHandler = this.debugClientHandlers.get(i);

                if (debugClientHandler.isRunning() && debugClientHandler.isMatch(cachedBodyHttpServletRequest)) {
                    log.info("DebugLib: URL {} matched. Will be forwarding to client {}", cachedBodyHttpServletRequest.getRequestURI(), debugClientHandler.getClientId());
                    matched = true;
                    HttpResponseInfo clientResponse = debugClientHandler.forwardHttpRequestToClient(cachedBodyHttpServletRequest);

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
        } catch (Throwable e) {
            log.error(LOG_ERROR_PREFIX + e.getMessage(), e);
        }

        if (!matched) {
            filterChain.doFilter(cachedBodyHttpServletRequest, servletResponse);
        }
    }

    public void addDebugClient(ClientHandler debugClientHandler) {
        log.info("DebugLib: Add new client {}", debugClientHandler.getClientId());
        this.debugClientHandlers.add(debugClientHandler);
        CompletableFuture.runAsync(() -> {
            synchronized (debugClientHandler) {
                try {
                    debugClientHandler.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    log.info("DebugLib: Remove client {} with status {}", debugClientHandler.getClientId(), debugClientHandler.getStatus());
                    this.debugClientHandlers.remove(debugClientHandler);
                } catch (Throwable e) {
                    log.error(LOG_ERROR_PREFIX + e.getMessage(), e);
                }
            }
        }, executor);
    }

    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void destroy() {

    }
}
