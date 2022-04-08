package io.github.tontu89.debugclientagent;

import io.github.tontu89.debugserverlib.model.MessageRequest;
import io.github.tontu89.debugserverlib.model.ServerClientMessage;
import io.github.tontu89.debugserverlib.utils.DebugUtils;
import lombok.extern.slf4j.Slf4j;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.github.tontu89.debugserverlib.utils.Constants.POLL_QUEUE_TIMEOUT_MS;


@Slf4j
public class SocketCommunication implements AutoCloseable {
    private final int port;
    private final BlockingQueue<ServerClientMessage> messageToServerQueue;
    private final Executor executor;
    private final List<Future<Void>> serverMessageProcessingFutureList;
    private final Map<String, String> waitingServerResponseIdList;
    private final Map<String, ServerClientMessage> serverResponseResultList;
    private final ServerResponseConsumer<ServerClientMessage> serverRequestHandler;
    private final String ip;

    private Boolean stop;
    private DataOutputStream dos;
    private DataInputStream dis;
    private Socket clientSocket;


    public SocketCommunication(String ip, int port, ServerResponseConsumer<ServerClientMessage> serverRequestHandler) throws IOException {
        this.ip = ip;
        this.port = port;
        this.serverRequestHandler = serverRequestHandler;
        this.waitingServerResponseIdList = new ConcurrentHashMap<>();
        this.serverResponseResultList = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool();
        this.serverMessageProcessingFutureList = new ArrayList<>();
        this.messageToServerQueue = new LinkedBlockingQueue<>();
        this.stop = false;

        this.startServerConnection();
        this.startServerMessageProcessing();
        this.startSendingMessageToServer();
    }

    public ServerClientMessage sendMessage(ServerClientMessage message, boolean needToWaitResponse) {
        if (this.stop) {
            return null;
        } else {
            String messageId = null;

            if (message.getId() == null) {
                messageId = "CLIENT-" + UUID.randomUUID();
                message.setId(messageId);
            } else {
                messageId = message.getId();
            }

            synchronized (messageId) {
                this.waitingServerResponseIdList.put(messageId, messageId);
                this.messageToServerQueue.add(message);

                if (message.getType() == ServerClientMessage.Type.REQUEST && message.getRequest().getCommand() == MessageRequest.Command.SERVER_EXIT) {
                    this.stopConnection();
                    return null;
                } else {
                    try {
                        if (needToWaitResponse) {
                            log.info("DebugAgent: Message ID {}: Sleeping to wait server processing", messageId);
                            while(!this.stop && !this.serverResponseResultList.containsKey(messageId)) {
                                messageId.wait(1000);
                            }
                        } else {
                            return null;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                log.info("DebugAgent: Message ID {}: Has result from server", messageId);
                return this.serverResponseResultList.get(messageId);
            }
        }

    }

    @Override
    public void close() {
        this.stopConnection();
    }

    public void waitToClose(int timeoutInMs) {
        synchronized (this.stop) {
            try {
                this.stop.wait(timeoutInMs);
            } catch (InterruptedException e) {
            }
        }
    }

    public boolean isRunning() {
        return !this.stop;

    }

    private void startServerConnection() throws IOException {
        this.clientSocket = new Socket(this.ip, this.port);

        this.dos = new DataOutputStream(this.clientSocket.getOutputStream());
        this.dis = new DataInputStream(this.clientSocket.getInputStream());
    }

    private void stopConnection() {
        log.info("DebugAgent: Stopping Socket Communication");
        try {
            this.stop = true;

            if (this.dos != null) {
                try {
                    this.dos.close();
                } catch (IOException e) {
                    log.error("DebugAgent: exception", e);
                }
            }

            if (this.dis != null) {
                try {
                    this.dis.close();
                } catch (IOException e) {
                    log.error("DebugAgent: exception", e);
                }
            }

            if (this.clientSocket != null && !this.clientSocket.isClosed()) {
                try {
                    this.clientSocket.close();
                } catch (IOException e) {
                    log.error("DebugAgent: exception", e);
                }
            }

            this.serverMessageProcessingFutureList.forEach(f -> {
                if (!f.isDone()) {
                    try {
                        f.get();
                    } catch (InterruptedException | ExecutionException e) {
                        log.error("DebugAgent: exception", e);
                    }
                }
            });

            synchronized (this.stop) {
                this.stop.notifyAll();
            }

            log.info("DebugAgent: Stopped Socket Communication");
        } catch (Throwable e) {
            log.error("DebugAgent: exception " + e.getMessage(), e);
        }
    }

    private void startServerMessageProcessing() {
        this.serverMessageProcessingFutureList.add(CompletableFuture.runAsync(() -> {
            try {
                while (!this.stop) {
                    try {
                        ServerClientMessage message = DebugUtils.readMessage(this.dis);

                        if (message.getType() == ServerClientMessage.Type.REQUEST) {
                            CompletableFuture.runAsync(() -> this.serverRequestHandler.action(message), this.executor);
                        } else if (message.getType() == ServerClientMessage.Type.RESPONSE) {
                            String messageId = this.waitingServerResponseIdList.get(message.getId());
                            this.waitingServerResponseIdList.remove(messageId);
                            this.serverResponseResultList.put(messageId, message);
                            log.info("DebugAgent: Message ID {}: Notify thread to wakeup to process server result", messageId);
                            synchronized (messageId) {
                                messageId.notifyAll();
                            }
                        } else {
                            throw new UnsupportedOperationException(message.toString());
                        }
                    } catch (EOFException e) {
                        break;
                    } catch (SocketException e) {
                        log.error("DebugAgent: exception " + e.getMessage(), e);
                        break;
                    } catch (Throwable e) {
                        log.error("DebugAgent: exception " + e.getMessage(), e);
                    }
                }
            } catch (Throwable e) {
                log.error("DebugAgent: exception " + e.getMessage(), e);
            }
            CompletableFuture.runAsync(() -> this.close(), this.executor);
        }, this.executor));
    }

    private void startSendingMessageToServer() {
        this.serverMessageProcessingFutureList.add(CompletableFuture.runAsync(() -> {
            try {
                while (!this.stop || !this.messageToServerQueue.isEmpty()) {
                    try {
                        ServerClientMessage message = this.messageToServerQueue.poll(POLL_QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        if (message != null) {
                            DebugUtils.writeMessage(this.dos, message);
                        }
                    } catch (IOException e) {
                        log.error("DebugAgent: exception", e);
                        break;
                    } catch (InterruptedException e) {
                    }
                }
            } catch (Throwable e) {
                log.error("DebugAgent: exception " + e.getMessage(), e);
            }
            CompletableFuture.runAsync(() -> this.close(), this.executor);
        }, this.executor));
    }
}
