package io.github.tontu89.debugserverlib;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.tontu89.debugserverlib.model.FilterRequest;
import io.github.tontu89.debugserverlib.model.FilterRequestMatchPattern;
import io.github.tontu89.debugserverlib.model.HttpRequestInfo;
import io.github.tontu89.debugserverlib.model.HttpResponseInfo;
import io.github.tontu89.debugserverlib.model.MessageRequest;
import io.github.tontu89.debugserverlib.model.MessageResponse;
import io.github.tontu89.debugserverlib.model.ServerClientMessage;
import io.github.tontu89.debugserverlib.utils.DebugUtils;
import io.github.tontu89.debugserverlib.utils.HttpUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.tontu89.debugserverlib.utils.Constants.LOG_ERROR_PREFIX;
import static io.github.tontu89.debugserverlib.utils.Constants.MAX_REQUEST_TIME_OUT_MS;
import static io.github.tontu89.debugserverlib.utils.Constants.OBJECT_MAPPER;
import static io.github.tontu89.debugserverlib.utils.Constants.POLL_QUEUE_TIMEOUT_MS;

@Slf4j
public class ClientHandler extends Thread implements AutoCloseable {
    public enum Status {RUNNING, STOPPED, NOT_RUNNING}

    private final BlockingQueue<ServerClientMessage> messageRequestFromClientQueue;
    private final BlockingQueue<ServerClientMessage> messageResponseFromClientQueue;
    private final BlockingQueue<ServerClientMessage> messageToClientQueue;
    private final DataInputStream dis;
    private final DataOutputStream dos;
    private final Executor executor;
    private final FilterRequest debugFilterRequest;
    private final Map<String, ServerClientMessage> responseForServerRequest;
    private final Map<String, String> serverRequestId;
    private final Socket socket;

    private boolean stop;

    private Status status;
    private Future<Void> sendMessageToClientFuture;
    private Future<Void> processClientRequestFuture;
    private Future<Void> processClientResponseFuture;


    public ClientHandler(DataInputStream dis, DataOutputStream dos, Socket socket) {
        this.socket = socket;
        this.dis = dis;
        this.dos = dos;
        this.status = Status.NOT_RUNNING;
        this.debugFilterRequest = new FilterRequest();
        this.executor = Executors.newCachedThreadPool();
        this.messageRequestFromClientQueue = new LinkedBlockingQueue<>();
        this.messageResponseFromClientQueue = new LinkedBlockingQueue<>();
        this.messageToClientQueue = new LinkedBlockingQueue<>();
        this.responseForServerRequest = new ConcurrentHashMap<>();
        this.serverRequestId = new ConcurrentHashMap<>();
        this.stop = false;
    }

    @Override
    @SneakyThrows
    public void run() {
        ServerClientMessage receivedMessage;

        try {
            startSendMessageToClient();
            startProcessClientRequest();
            startProcessClientResponse();

            while (!this.stop && !this.socket.isClosed()) {
                try {
                    this.status = Status.RUNNING;

                    receivedMessage = DebugUtils.readMessage(this.dis);

                    if (receivedMessage.getType() == ServerClientMessage.Type.REQUEST) {
                        this.messageRequestFromClientQueue.add(receivedMessage);
                    } else if (receivedMessage.getType() == ServerClientMessage.Type.RESPONSE) {
                        this.messageResponseFromClientQueue.add(receivedMessage);
                    } else {
                        log.error("DebugLib: Unsupported message type {}", receivedMessage);
                    }
                } catch (EOFException e) {
                    this.stop = true;
                } catch (SocketException e) {
                    this.stop = true;
                    log.error(LOG_ERROR_PREFIX, e);
                } catch (IOException e) {
                    log.error(LOG_ERROR_PREFIX, e);
                }
            }
        } catch (Exception e) {
            log.error(LOG_ERROR_PREFIX, e);
        }

        this.close();
    }

    @SneakyThrows
    public HttpResponseInfo forwardRequestToClient(HttpServletRequest httpRequest) {
        HttpRequestInfo requestInfo = HttpRequestInfo.fromHttpRequest(httpRequest, true, false);
        MessageRequest messageRequest = MessageRequest.builder()
                .command(MessageRequest.Command.CLIENT_EXECUTE_HTTP_REQUEST)
                .data(OBJECT_MAPPER.writeValueAsString(requestInfo))
                .build();
        String messageId = "SERVER-" + UUID.randomUUID();
        this.messageToClientQueue.add(ServerClientMessage.builder()
                .id(messageId)
                .type(ServerClientMessage.Type.REQUEST)
                .request(messageRequest).build());

        this.serverRequestId.put(messageId, messageId);

        synchronized (messageId) {
            try {
                log.info("DebugLib: Server request {}: Wait response", messageId);
                messageId.wait(MAX_REQUEST_TIME_OUT_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            if (!this.responseForServerRequest.containsKey(messageId)) {
                log.error("DebugLib: Server request {}: timeout after {} minutes", messageId, MAX_REQUEST_TIME_OUT_MS / 1000 / 60);
                return HttpResponseInfo.builder().httpStatus(504).build();
            } else {
                log.info("DebugLib: Server request {}: Received response", messageId);
                ServerClientMessage message = this.responseForServerRequest.get(messageId);
                log.info("DebugLib: Server request {}: Received response with: {}", messageId, message);

                if (!messageId.equals(message.getId())) {
                    throw new Exception("Unexpected error");
                }

                return DebugUtils.escapedJsonStringToObject(message.getResponse().getData(), HttpResponseInfo.class);
            }
        }
    }

    private void startSendMessageToClient() {
        this.sendMessageToClientFuture = CompletableFuture.runAsync(() -> {
            try {
                while (!this.stop || !this.messageToClientQueue.isEmpty()) {
                    ServerClientMessage message = pollMessageFromQueue(this.messageToClientQueue);

                    if (message == null) continue;

                    DebugUtils.writeMessage(this.dos, message);
                }
            } catch (Exception e) {
                log.error(LOG_ERROR_PREFIX, e);
                this.closeAsync();
            }
        }, this.executor);
    }

    private void startProcessClientRequest() {
        this.processClientRequestFuture = CompletableFuture.runAsync(() -> {
            while (!this.stop) {
                ServerClientMessage message = pollMessageFromQueue(this.messageRequestFromClientQueue);

                if (message == null) continue;

                MessageRequest messageRequest = message.getRequest();
                MessageResponse messageResponse = MessageResponse.builder()
                        .status(HttpStatus.OK.value())
                        .build();

                try {
                    switch (messageRequest.getCommand()) {
                        case SERVER_EXIT:
                            this.stop = true;
                            break;
                        case SERVER_GET_ENV:
                            messageResponse.setData(System.getenv());
                            break;
                        case SERVER_GET_PROP:
                            messageResponse.setData(System.getProperties());
                            break;
                        case SERVER_ADD_FILTER_PATTERN:
                            List<FilterRequestMatchPattern> matchPatterns = DebugUtils.escapedJsonStringToObject(messageRequest.getData(), new TypeReference<>() {
                            });
                            matchPatterns.forEach(e -> e.init());
                            this.debugFilterRequest.addPattern(matchPatterns);
                            break;
                        case SERVER_GET_ALL_FILTER_PATTERN:
                            messageResponse.setData(this.debugFilterRequest.getMatchPatterns());
                            break;
                        case SERVER_CLEAR_ALL_FILTER_PATTERN:
                            this.debugFilterRequest.getMatchPatterns().clear();
                            break;
                        case SERVER_EXECUTE_HTTP_REQUEST:
                            HttpRequestInfo clientRequestInfo = DebugUtils.escapedJsonStringToObject(messageRequest.getData(), HttpRequestInfo.class);
                            messageResponse.setData(this.executeClientHttpRequest(clientRequestInfo));

                    }
                } catch (Exception e) {
                    log.error(LOG_ERROR_PREFIX, e);
                    messageResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                }
                ServerClientMessage serverClientMessage = ServerClientMessage.builder()
                        .id(message.getId())
                        .type(ServerClientMessage.Type.RESPONSE)
                        .response(messageResponse)
                        .build();
                this.messageToClientQueue.add(serverClientMessage);
            }
        }, this.executor);
    }

    private void startProcessClientResponse() {
        this.processClientResponseFuture = CompletableFuture.runAsync(() -> {
            try {
                while (!this.stop || !this.messageResponseFromClientQueue.isEmpty()) {
                    ServerClientMessage message = pollMessageFromQueue(this.messageResponseFromClientQueue);

                    if (message == null) continue;

                    try {
                        String messageId = this.serverRequestId.get(message.getId());
                        this.responseForServerRequest.put(messageId, message);

                        synchronized (messageId) {
                            messageId.notifyAll();
                        }
                    } catch (Exception e) {
                        log.error(message.toString(), e);
                    }
                }
            } catch (Exception e) {
                log.error(LOG_ERROR_PREFIX, e);
                this.closeAsync();
            }
        }, this.executor);
    }

    public boolean isMatch(HttpServletRequest httpRequest) {
        if (this.isRunning()) {
            try {
                String httpRequestJsonFormat = OBJECT_MAPPER.writeValueAsString(HttpRequestInfo.fromHttpRequest(httpRequest, true, true));

                return this.debugFilterRequest.isMatch(httpRequestJsonFormat);
            } catch (Exception e) {
                log.error(LOG_ERROR_PREFIX, e);
            }
        }
        return false;
    }


    @Override
    public void close() {
        this.stop = true;
        this.status = Status.STOPPED;

        log.info("Client " + this.socket + " sends exit...");
        log.info("Closing this connection.");
        log.info("Connection closed");

        Arrays.asList(this.processClientRequestFuture, this.sendMessageToClientFuture, this.processClientResponseFuture).forEach((f) -> {
            try {
                if (!f.isDone()) {
                    f.get();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error(LOG_ERROR_PREFIX, e);
            }
        });

        this.serverRequestId.forEach((key, value) -> {
            try {
                synchronized (value) {
                    value.notifyAll();
                }
            } catch (Exception e) {
                log.error("DebugLib: Error happen with ({}, {}) when notify object for closing", key, value);
                log.error(LOG_ERROR_PREFIX, e);
            }
        });
        // closing resources
        if (this.dis != null) {
            try {
                this.dis.close();
            } catch (IOException e) {
                log.error(LOG_ERROR_PREFIX, e);
            }
        }

        if (this.dos != null) {
            try {
                this.dos.close();
            } catch (IOException e) {
                log.error(LOG_ERROR_PREFIX, e);
            }
        }

        if (this.socket != null && !this.socket.isClosed()) {
            try {
                this.socket.close();
            } catch (IOException e) {
                log.error(LOG_ERROR_PREFIX, e);
            }
        }
    }

    public boolean isRunning() {
        return this.status == Status.RUNNING;
    }

    public Status getStatus() {
        return this.status;
    }

    private ServerClientMessage pollMessageFromQueue(BlockingQueue<ServerClientMessage> queue) {
        try {
            return queue.poll(POLL_QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    @SneakyThrows
    private HttpResponseInfo executeClientHttpRequest(HttpRequestInfo httpRequestInfo) {
        AtomicReference<HttpURLConnection> connection = new AtomicReference<>();

        String responseString = HttpUtils.executeHttpRequest(
                httpRequestInfo.getUri(),
                null,
                httpRequestInfo.getMethod(),
                httpRequestInfo.getHeaders(),
                httpRequestInfo.getPayload(),
                httpURLConnection -> connection.set(httpURLConnection)
        );

        HttpResponseInfo responseInfo = HttpResponseInfo.builder()
                .httpStatus(connection.get().getResponseCode())
                .headers(HttpUtils.getHttpResponseHeader(connection.get()))
                .payload(responseString)
                .build();

        return responseInfo;
    }

    private void closeAsync() {
        CompletableFuture.runAsync(() -> this.close(), this.executor);
    }



}
