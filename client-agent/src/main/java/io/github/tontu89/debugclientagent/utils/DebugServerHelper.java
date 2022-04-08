package io.github.tontu89.debugclientagent.utils;

import io.github.tontu89.debugclientagent.DebugServerCommunication;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.tontu89.debugserverlib.model.HttpRequestInfo;
import io.github.tontu89.debugserverlib.model.HttpResponseInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DebugServerHelper {
    private List<DebugServerCommunication> servers;

    public DebugServerHelper(List<DebugServerCommunication> servers) {
        this.servers = servers;
    }

    public boolean isRunning() {
        return !this.servers.stream().filter(e -> e.isRunning()).findFirst().isEmpty();
    }

    public void waitToClose(int timeoutInMs) {
        for(DebugServerCommunication server: servers) {
            if (server.isRunning()) {
                server.waitToClose(timeoutInMs);
                break;
            }
        }
    }

    public HttpResponseInfo forwardRequestToServer(HttpRequestInfo httpRequestInfo) throws JsonProcessingException {
        for(DebugServerCommunication server: servers) {
            if (server.isRunning()) {
                return server.forwardRequestToServer(httpRequestInfo);
            }
        }
        log.error("DebugAgent: All connection are death");
        return HttpResponseInfo.builder().httpStatus(504).build();
    }
}
