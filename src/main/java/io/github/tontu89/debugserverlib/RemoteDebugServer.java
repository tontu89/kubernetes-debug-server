package io.github.tontu89.debugserverlib;

import io.github.tontu89.debugserverlib.config.RemoteDebugServerConfig;
import io.github.tontu89.debugserverlib.filter.DebugServerSpringFilter;
import io.github.tontu89.debugserverlib.utils.HttpsTrustManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static io.github.tontu89.debugserverlib.utils.Constants.LOG_ERROR_PREFIX;
import static io.github.tontu89.debugserverlib.utils.Constants.SPRING_PROFILE_NAME;

@Profile(SPRING_PROFILE_NAME)
@Component
@Slf4j
public class RemoteDebugServer implements AutoCloseable {
    private boolean stop;

    private RemoteDebugServerConfig remoteDebugServerConfig;
    private DebugServerSpringFilter debugServerSpringFilter;
    private ServerSocket server = null;

    public RemoteDebugServer(DebugServerSpringFilter debugServerSpringFilter, RemoteDebugServerConfig remoteDebugServerConfig, Environment env) {
        this.debugServerSpringFilter = debugServerSpringFilter;
        this.remoteDebugServerConfig = remoteDebugServerConfig;

        if (this.isEnableRemoteDebug(env)) {
            log.info("DebugLib: Prepare to load debug server");
            CompletableFuture.runAsync(() -> this.start(), Executors.newSingleThreadExecutor());
        }
    }

    private void start() {
        try {
            HttpsTrustManager.allowAllSSL();

            Socket socket = null;

            // Try to start on default port
            if (!this.startServer(this.remoteDebugServerConfig.getPort())) {

                // Try to start on random port
                this.startServer(null);
            }

            log.info("DebugLib: Started debug server");

            while (!this.stop) {
                try {
                    // socket object to receive incoming client requests
                    socket = server.accept();

                    log.info("DebugLib: A new client is connected : " + socket);

                    // obtaining input and out streams
                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                    log.info("DebugLib: Assigning new thread for this client");

                    // create a new thread object
                    ClientHandler t = new ClientHandler(this.remoteDebugServerConfig, dis, dos, socket);

                    // Invoking the start() method
                    t.start();

                    this.debugServerSpringFilter.addDebugClient(t);

                } catch (Throwable e) {
                    closeSocket(socket);
                    log.error(LOG_ERROR_PREFIX + e.getMessage(), e);
                }
            }
        } catch (Throwable e) {
            log.error(LOG_ERROR_PREFIX + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        this.stop = true;

        if (this.server != null && !this.server.isClosed()) {
            try {
                this.server.close();
            } catch (IOException e) {
                log.error(LOG_ERROR_PREFIX + e.getMessage(), e);
            }
        }
    }

    private void closeSocket(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Throwable e1) {
            log.error(LOG_ERROR_PREFIX + e1.getMessage(), e1);
        }
    }

    private boolean startServer(Integer port) {
        try {
            if (port == null) {
                this.server = new ServerSocket();
            } else {
                this.server = new ServerSocket(port);
            }
            log.info("DebugLib: Started debug server on port {}", this.server.getLocalPort());
        } catch (IOException e) {
            log.error("DebugLib: Cannot start debug server on port {}", port);
            log.error(LOG_ERROR_PREFIX + e.getMessage(), e);
            return false;
        }
        return true;
    }

    private boolean isEnableRemoteDebug(Environment env) {
        if (env != null) {
            String[] activeProfiles = env.getActiveProfiles();
            return Optional.ofNullable(activeProfiles).map(a -> Arrays.stream(a).filter(p -> SPRING_PROFILE_NAME.equalsIgnoreCase(p)).count()).orElse(0L) > 0;
        } else {
            log.info("DebugLib: Environment null pointer");
            return false;
        }
    }
}