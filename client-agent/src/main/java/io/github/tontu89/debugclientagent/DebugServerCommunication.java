package io.github.tontu89.debugclientagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.tontu89.debugserverlib.model.FilterRequestMatchPattern;
import io.github.tontu89.debugserverlib.model.HttpRequestInfo;
import io.github.tontu89.debugserverlib.model.HttpResponseInfo;
import io.github.tontu89.debugserverlib.model.MessageRequest;
import io.github.tontu89.debugserverlib.model.MessageResponse;
import io.github.tontu89.debugserverlib.model.ServerClientMessage;
import io.github.tontu89.debugserverlib.utils.DebugUtils;
import io.github.tontu89.debugserverlib.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.HttpStatus;


import java.io.File;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class DebugServerCommunication implements AutoCloseable {
    private final int debugServerPort;
    private final String clientName;
    private final String debugServerIp;
    private final String localWebServerAddress;

    private SocketCommunication socketCommunication;

    public DebugServerCommunication(String clientName, String debugServerIp, int debugServerPort, String localWebServerAddress) throws Exception {
        this.clientName = clientName;
        this.debugServerIp = debugServerIp;
        this.debugServerPort = debugServerPort;
        this.localWebServerAddress = localWebServerAddress.endsWith("/") ? (localWebServerAddress.substring(0, localWebServerAddress.length() - 1)) : localWebServerAddress;
        this.socketCommunication = new SocketCommunication(this.debugServerIp, this.debugServerPort, (e) -> this.serverRequestHandler(e));
        this.assignClientName();
    }

    public Map<String, String> getServerEnvironments() throws Exception {
        try {
            ServerClientMessage responseMessage = this.socketCommunication.sendMessage(ServerClientMessage.builder()
                    .type(ServerClientMessage.Type.REQUEST)
                    .request(MessageRequest.builder().command(MessageRequest.Command.SERVER_GET_ENV).build())
                    .build(), true);

            if (responseMessage.getResponse().getStatus() != HttpStatus.SC_OK) {
                throw new Exception("Cannot get server environment");
            }
            return DebugUtils.base64StringToObject(responseMessage.getResponse().getDataBase64(), new TypeReference<TreeMap<String, String>>() {});
        } catch (Throwable e) {
            log.error("DebugAgent: exception " + e.getMessage(), e);
            this.socketCommunication.close();
            log.error("DebugAgent: QUIT APP BECAUSE CANNOT SYNC ENVIRONMENT");
            throw e;
        }
    }

    public Map<String, String> getServerSystemProperties() throws Exception {
        try {
            ServerClientMessage responseMessage = this.socketCommunication.sendMessage(ServerClientMessage.builder()
                    .type(ServerClientMessage.Type.REQUEST)
                    .request(MessageRequest.builder().command(MessageRequest.Command.SERVER_GET_PROP).build())
                    .build(), true);

            if (responseMessage.getResponse().getStatus() != HttpStatus.SC_OK) {
                throw new Exception("Cannot get server environment");
            }

            return DebugUtils.base64StringToObject(responseMessage.getResponse().getDataBase64(), new TypeReference<TreeMap<String, String>>() {});
        } catch (Throwable e) {
            log.error("DebugAgent: exception " + e.getMessage(), e);
            this.socketCommunication.close();
            log.error("DebugAgent: QUIT APP BECAUSE CANNOT SYNC ENVIRONMENT");
            throw e;
        }
    }

    public void downloadFile(String sourceFilePath, String targetFilePath) throws Exception {
        File targetFile = new File(targetFilePath);
        File parentTargetPath = targetFile.getParentFile();

        if ("/".equals(parentTargetPath.getPath())) {
            throw new Exception("Cannot create file at root path");
        } else {
            if (targetFile.exists() && !targetFile.isFile()) {
                throw new Exception(targetFilePath + " is directory");
            } else if (targetFile.exists() && targetFile.isFile() && (!targetFile.canWrite() || !targetFile.canRead())) {
                StringJoiner stringJoiner = new StringJoiner("\n");
                stringJoiner.add("Cannot write or read file on " + targetFile + ": Permission denied");
                stringJoiner.add("Try to run command in shell: sudo chown -R $USER: " + targetFile.getPath());
                throw new Exception(stringJoiner.toString());
            } else if (!parentTargetPath.canWrite() || !parentTargetPath.canRead()) {
                StringJoiner stringJoiner = new StringJoiner("\n");
                stringJoiner.add("Cannot write or read file on directory" + parentTargetPath + ": Permission denied");
                stringJoiner.add("Try to run command in shell: sudo mkdir -p " + parentTargetPath.getPath() + " && sudo chown -R $USER: " + parentTargetPath.getPath());
                throw new Exception(stringJoiner.toString());
            } else if (!parentTargetPath.exists()) {
                Files.createDirectories(parentTargetPath.toPath());
            }
        }

        ServerClientMessage responseMessage = this.socketCommunication.sendMessage(ServerClientMessage.builder()
                .type(ServerClientMessage.Type.REQUEST)
                .request(MessageRequest.builder()
                        .command(MessageRequest.Command.SERVER_DOWNLOAD_FILE)
                        .dataBase64(DebugUtils.objectToBase64String(sourceFilePath))
                        .build())
                .build(), true);
        MessageResponse responseData = responseMessage.getResponse();

        byte[] fileContent = Base64.getDecoder().decode(responseData.getDataBase64());

        if (responseData.getStatus() == 200 && fileContent != null) {
            File file = new File(targetFilePath);
            file.delete();
            Files.write(file.toPath(), fileContent, StandardOpenOption.CREATE);
        } else {
            throw new Exception(String.format("DebugAgent: Download file %s has error: %s", sourceFilePath, fileContent == null ? null : new String(fileContent)));
        }
    }


    public void addRequestFilter(FilterRequestMatchPattern filterRequestMatchPattern) throws Exception {
        this.addRequestFilter(new ArrayList<>(){{
            add(filterRequestMatchPattern);
        }});
    }

    public void addRequestFilter(List<FilterRequestMatchPattern> filterRequestMatchPatterns) throws Exception {
        ServerClientMessage response = this.socketCommunication.sendMessage(ServerClientMessage.builder()
                .type(ServerClientMessage.Type.REQUEST)
                .request(MessageRequest.builder()
                        .command(MessageRequest.Command.SERVER_ADD_FILTER_PATTERN)
                        .dataBase64(DebugUtils.objectToBase64String(filterRequestMatchPatterns))
                        .build())
                .build(), true);

        if (response.getResponse().getStatus() == HttpStatus.SC_OK) {
            log.info("DebugAgent: Successfully added filter");
        } else {
            throw new Exception("Error happened! Cannot add filter");
        }
    }

    public HttpResponseInfo forwardRequestToServer(HttpRequestInfo clientHttpResponseInfo) throws JsonProcessingException {
        try {
            ServerClientMessage messageRequest = ServerClientMessage.builder()
                    .type(ServerClientMessage.Type.REQUEST)
                    .request(MessageRequest.builder()
                            .command(MessageRequest.Command.SERVER_EXECUTE_HTTP_REQUEST)
                            .dataBase64(DebugUtils.objectToBase64String(clientHttpResponseInfo))
                            .build())
                    .build();

            log.debug("DebugAgent: Forward HTTP Request to debug server: {}", clientHttpResponseInfo);
            long startTime = System.currentTimeMillis();

            ServerClientMessage messageResponse = this.socketCommunication.sendMessage(messageRequest, true);

            log.debug("DebugAgent: Received response in {}ms from server: {}", System.currentTimeMillis() - startTime, messageResponse);

            if (messageResponse.getResponse().getStatus() == HttpStatus.SC_OK) {
                return DebugUtils.base64StringToObject(messageResponse.getResponse().getDataBase64(), HttpResponseInfo.class);
            } else {
                log.error("DebugAgent: Something wrong happened when forward request {}", clientHttpResponseInfo);
                return HttpResponseInfo.builder().httpStatus(500).build();
            }
        } catch (Throwable e) {
            log.error("DebugAgent: exception " + e.getMessage(), e);
        }
        return HttpResponseInfo.builder().httpStatus(500).build();
    }

    @Override
    public void close() {
        this.socketCommunication.close();
    }

    public void waitToClose(int timeoutInMs) {
        this.socketCommunication.waitToClose(timeoutInMs);
    }

    public boolean isRunning() {
        return this.socketCommunication.isRunning();
    }

    private void serverRequestHandler(ServerClientMessage message) {
        try {
            if (message.getRequest().getCommand() == MessageRequest.Command.CLIENT_EXECUTE_HTTP_REQUEST) {
                HttpRequestInfo serverRequestInfo = DebugUtils.base64StringToObject(message.getRequest().getDataBase64(), HttpRequestInfo.class);
                AtomicReference<HttpURLConnection> connection = new AtomicReference<>();

                log.debug("DebugAgent: Start Process Server Request {}", serverRequestInfo);

                HttpResponseInfo clientResponseInfo = HttpUtils.executeHttpRequestByRest(
                        this.localWebServerAddress,
                        serverRequestInfo.getUri(),
                        serverRequestInfo.getMethod(),
                        serverRequestInfo.getHeaders(),
                        serverRequestInfo.getPayload());

                ServerClientMessage clientResponseMessage = ServerClientMessage.builder()
                        .id(message.getId())
                        .type(ServerClientMessage.Type.RESPONSE)
                        .response(MessageResponse.builder()
                                .status(200)
                                .dataBase64(DebugUtils.objectToBase64String(clientResponseInfo))
                                .build())
                        .build();

                this.socketCommunication.sendMessage(clientResponseMessage, false);

            } else if (message.getRequest().getCommand() == MessageRequest.Command.HEART_BEAT) {
                ServerClientMessage clientResponseMessage = ServerClientMessage.builder()
                        .id(message.getId())
                        .type(ServerClientMessage.Type.RESPONSE)
                        .response(MessageResponse.builder()
                                .status(200)
                                .build())
                        .build();

                this.socketCommunication.sendMessage(clientResponseMessage, false);
            } else {
                throw new UnsupportedOperationException(message.toString());
            }
        } catch (Throwable e) {
            log.error("DebugAgent: exception " + e.getMessage(), e);
        }
    }

    private void assignClientName() throws Exception {
        ServerClientMessage responseMessage = this.socketCommunication.sendMessage(ServerClientMessage.builder()
                .type(ServerClientMessage.Type.REQUEST)
                .request(MessageRequest.builder()
                        .command(MessageRequest.Command.SERVER_SET_CLIENT_NAME)
                        .dataBase64(DebugUtils.objectToBase64String(this.clientName))
                        .build())
                .build(), true);
        MessageResponse responseData = responseMessage.getResponse();

        String reason = Optional.ofNullable(responseData.getDataBase64()).map(d -> new String(Base64.getDecoder().decode(d))).orElse(null);

        if (responseData.getStatus() != 200) {
//            throw new Exception("Cannot set client name with error [{}]" + reason);
            log.error("Cannot set client name with error [{}]" + reason);
        }
    }

}
