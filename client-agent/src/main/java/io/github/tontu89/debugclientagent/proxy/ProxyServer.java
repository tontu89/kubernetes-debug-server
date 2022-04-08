package io.github.tontu89.debugclientagent.proxy;

import io.github.tontu89.debugclientagent.config.SSLConfig;
import io.github.tontu89.debugclientagent.utils.DebugServerHelper;
import lombok.extern.slf4j.Slf4j;

import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
public class ProxyServer implements AutoCloseable {

    private boolean running;
    private DebugServerHelper debugServer;
    private Executor executor;
    private List<Future<Void>> servicingThreads;
    private ServerSocket serverSocket;
    private SSLConfig sslConfig = null;

    public ProxyServer(int httpPort, DebugServerHelper debugServer) {
        this.debugServer = debugServer;
        this.servicingThreads = new ArrayList<>();
        this.executor = Executors.newCachedThreadPool();
        this.sslConfig = new SSLConfig();

        try {
            this.serverSocket = new ServerSocket(httpPort);
            log.info("DebugAgent: HTTP(s) Proxy server is waiting for client on port {} ..", serverSocket.getLocalPort());
            this.running = true;
        } catch (SocketException e) {
            log.error("DebugAgent: exception", e);
        } catch (SocketTimeoutException e) {
            log.error("DebugAgent: exception", e);
        } catch (IOException e) {
            log.error("DebugAgent: exception", e);
        }
    }

    public void start() {
        this.servicingThreads.add(CompletableFuture.runAsync(() -> {
            while (this.running) {
                try {
                    Socket socket = serverSocket.accept();
                    String uuid = UUID.randomUUID().toString();
                    log.debug("DebugAgent: new proxy socket connection {}", uuid);
                    this.servicingThreads.add(CompletableFuture.runAsync(() -> {
                        try {
                            log.debug("DebugAgent: start new handler for proxy connection {} with thread {}", uuid, Thread.currentThread().getId());
                            try (RequestHandler requestHandler = new RequestHandler(uuid, this.sslConfig, this.debugServer)) {
                                requestHandler.start(socket);
                            }
                            log.debug("DebugAgent: end for proxy connection {} with thread {}", uuid, Thread.currentThread().getId());

                        } catch (Throwable e) {
                            log.error("DebugAgent: exception", e);
                        }
                    }, this.executor));

                } catch (EOFException | SocketException e) {
                    log.error("DebugAgent: Proxy server is closed");
                    log.error("DebugAgent: exception", e);
                    break;
                } catch (Throwable e) {
                    log.error("DebugAgent: exception", e);
                }
            }
            CompletableFuture.runAsync(() -> this.close(), this.executor);
        }, this.executor));
    }

    @Override
    public void close() {
        log.info("DebugAgent: Stopping Http Proxy Server..");
        this.running = false;

        // Close Server Socket
        try {
            log.info("DebugAgent: Terminating Connection");
            this.serverSocket.close();
        } catch (Throwable e) {
            log.error("DebugAgent: exception", e);
        }

        this.servicingThreads.forEach(f -> {
            if (!f.isDone()) {
                try {
                    f.get();
                } catch (Throwable e) {
                    log.error("DebugAgent: exception", e);
                }
            }
        });

        log.info("DebugAgent: Stopped Http Proxy Server..");
    }

    public boolean isRunning() {
        return running;
    }
}
