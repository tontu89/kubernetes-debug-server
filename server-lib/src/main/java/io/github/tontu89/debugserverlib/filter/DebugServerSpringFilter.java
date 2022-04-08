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
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static io.github.tontu89.debugserverlib.utils.Constants.LOG_ERROR_PREFIX;
import static io.github.tontu89.debugserverlib.utils.Constants.SPRING_PROFILE_NAME;


@Profile(SPRING_PROFILE_NAME)
@Component
@Slf4j
public class DebugServerSpringFilter implements Filter {
    private final Executor executor = Executors.newCachedThreadPool();
    private final CopyOnWriteArrayList<ClientHandler> debugClientHandlers = new CopyOnWriteArrayList<>();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        boolean matched = false;

        HttpServletRequest httpServletRequest = (HttpServletRequest)servletRequest;

        String uri = httpServletRequest.getRequestURI();

        if (!uri.startsWith("/actuator")) {
            CachedBodyHttpServletRequest cachedBodyHttpServletRequest = new CachedBodyHttpServletRequest(httpServletRequest);
            HttpServletResponse res = (HttpServletResponse) servletResponse;

            // Remove transfer-encoding and content-encoding before further processing
            HttpResponseInfo.removeEncodingHeader(cachedBodyHttpServletRequest.getHeaders());

            log.info("DebugLib: Check matching request for {}", uri);

            try {
                for (int i = 0; i < this.debugClientHandlers.size(); ++i) {
                    ClientHandler debugClientHandler = this.debugClientHandlers.get(i);

                    if (debugClientHandler.isRunning() && debugClientHandler.isMatch(cachedBodyHttpServletRequest)) {
                        log.info("DebugLib: URL {} matched. Will be forwarding to client [{}][{}]", cachedBodyHttpServletRequest.getRequestURI(), debugClientHandler.getClientName(), debugClientHandler.getClientId());
                        matched = true;
                        HttpResponseInfo clientResponse = debugClientHandler.forwardHttpRequestToClient(cachedBodyHttpServletRequest);
                        Optional.ofNullable(clientResponse.getHeaders()).ifPresent(headers -> headers.forEach((name, value) -> res.setHeader(name, value)));
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
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    public void addDebugClient(ClientHandler debugClientHandler) {
        log.info("DebugLib: Add new client [{}][{}]", debugClientHandler.getClientName(), debugClientHandler.getClientId());
        this.debugClientHandlers.add(debugClientHandler);
        CompletableFuture.runAsync(() -> {
            synchronized (debugClientHandler) {
                try {
                    debugClientHandler.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    log.info("DebugLib: Remove client [{}][{}] with status {}", debugClientHandler.getClientName(), debugClientHandler.getClientId(), debugClientHandler.getStatus());
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
