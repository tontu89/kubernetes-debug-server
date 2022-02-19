package com.kubernetes.debugserver;

import com.kubernetes.debugserver.filter.DebugSpringFilter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class Server {
    //initialize socket and input stream
    private ServerSocket server = null;
    private DebugSpringFilter debugSpringFilter;

    public Server(DebugSpringFilter debugSpringFilter) {
        this.debugSpringFilter = debugSpringFilter;
        log.info("Prepare to load debug server");
        CompletableFuture.runAsync(() -> this.start());
    }

    @SneakyThrows
    private void start() {
        Socket socket = null;
        server = new ServerSocket(7777);

        log.info("Started debug server");

        while (true) {
            try {
                // socket object to receive incoming client requests
                socket = server.accept();

                System.out.println("A new client is connected : " + socket);

                // obtaining input and out streams
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                System.out.println("Assigning new thread for this client");

                // create a new thread object
                ClientHandler t = new ClientHandler(dis, dos, socket);

                // Invoking the start() method
                t.start();

                this.debugSpringFilter.addDebugClient(t);

            } catch (Exception e) {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }

                log.error(e.getMessage(), e);
            }
        }
    }

}