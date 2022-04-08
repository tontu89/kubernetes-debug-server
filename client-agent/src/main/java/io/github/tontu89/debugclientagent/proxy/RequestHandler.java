package io.github.tontu89.debugclientagent.proxy;

import io.github.tontu89.debugclientagent.config.SSLConfig;
import io.github.tontu89.debugclientagent.utils.DebugServerHelper;
import io.github.tontu89.debugclientagent.utils.SecurityUtils;
import io.github.tontu89.debugclientagent.utils.http.RawHttpRequest;
import io.github.tontu89.debugclientagent.utils.http.RawHttpResponse;
import io.github.tontu89.debugclientagent.utils.http.parser.HttpRequestParser;
import io.github.tontu89.debugserverlib.model.HttpRequestInfo;
import io.github.tontu89.debugserverlib.model.HttpResponseInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.httpclient.HttpStatus;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InvalidObjectException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class RequestHandler implements AutoCloseable {
    private static final URLCodec urlCodec = new URLCodec("UTF-8");
    private final String id;

    private DebugServerHelper debugServer;
    private ReentrantLock fileLock;
    private List<Socket> listClientSocket;
    private SSLConfig sslConfig;


    public RequestHandler(String id, SSLConfig sslConfig, DebugServerHelper debugServer) {
        this.id = id;
        this.debugServer = debugServer;
        this.sslConfig = sslConfig;
        this.fileLock = new ReentrantLock();
        this.listClientSocket = new ArrayList<>();
    }

    public void start(Socket clientSocket) throws IOException {
        this.processRequest(clientSocket);
    }

    public void processRequest(Socket clientSocket) throws IOException {
        log.info("DebugAgent: start process request by thread {} [{}]", Thread.currentThread().getId(), id);
        try {
            this.listClientSocket.add(clientSocket);
            clientSocket.setSoTimeout(10 * 60 * 1000);
            BufferedReader clientToProxyIs = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream proxyToClientOs = clientSocket.getOutputStream();

            while(true) {
                RawHttpRequest rawHttpRequest = HttpRequestParser.parseRequest(clientToProxyIs);

                String connType = rawHttpRequest.getHeaderParam("Proxy-Connection", "");
                if ("keep-alive".equals(connType.toLowerCase(Locale.ROOT))) {
                    clientSocket.setKeepAlive(true);
                }

                if ("CONNECT".equals(rawHttpRequest.getRequestType())) {
                    this.processHttpsRequest(clientSocket, proxyToClientOs, rawHttpRequest);

                    synchronized (this.listClientSocket) {
                        try {
                            log.info("DebugAgent: wait processing HTTPS [{}]", id);
                            this.listClientSocket.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        log.info("DebugAgent: finish processing HTTPS [{}]", id);
                    }
                } else {
                    HttpResponseInfo responseInfo = this.processHttpRequest(rawHttpRequest);

                    RawHttpResponse rawHttpResponse = new RawHttpResponse();
                    rawHttpResponse.setStatusLine(String.format("%s %d %s\r\n", rawHttpRequest.getHttpVersion(), responseInfo.getHttpStatus(), HttpStatus.getStatusText(responseInfo.getHttpStatus())));
                    rawHttpResponse.setHeaderFields(responseInfo.getHeaders());
                    rawHttpResponse.setPlainResponseBody(responseInfo.getPayload());
                    rawHttpResponse.writeTo(proxyToClientOs);
                }
                log.info("DebugAgent: end process request by thread {} [{}]", Thread.currentThread().getId(), id);
            }
        } catch (InvalidObjectException | SocketTimeoutException e) {
            log.info("Socket [{}] with thread [{}] has exception {}", this.id, Thread.currentThread().getId(), e.getMessage());
        }
    }

    private HttpResponseInfo processHttpRequest(RawHttpRequest rawHttpRequest) {
        try {
            HttpRequestInfo httpRequestInfo = HttpRequestInfo.builder()
                    .uri(urlCodec.decode(rawHttpRequest.getUrl().toString()))
                    .method(rawHttpRequest.getRequestType())
                    .headers(rawHttpRequest.getHeaders())
                    .payload(rawHttpRequest.getBody().length() > 0 ? rawHttpRequest.getBody().toString() : null)
                    .build();

            log.debug("DebugAgent: Handle request through proxy {} [{}]", httpRequestInfo, id);

            HttpResponseInfo responseInfo = this.debugServer.forwardRequestToServer(httpRequestInfo);

            log.debug("DebugAgent: Result of forward request [{}] [{}]", responseInfo, id);

            return responseInfo;
        } catch (Throwable e) {
            log.error("DebugAgent: exception [{" + id + "}]", e);
        }

        return HttpResponseInfo.builder().httpStatus(500).build();
    }

    private void processHttpsRequest(Socket clientSocket, OutputStream proxyToClientOs, RawHttpRequest rawHttpRequest) {
        // Handshake handler
        String hostname = rawHttpRequest.getUrl().getHost();
        String certFile = String.format("%s/%s.p12", this.sslConfig.getCertsFolder().toAbsolutePath(), hostname);

        try {
            if (!new File(certFile).exists()) {
                fileLock.lock();
                try {
                    SecurityUtils.createHostCert(hostname, certFile, this.sslConfig);
                } finally {
                    fileLock.unlock();
                }
            }

            proxyToClientOs.write(String.format("%s %d %s\r\n", rawHttpRequest.getHttpVersion(), 200, "Connection Established").getBytes(StandardCharsets.UTF_8));
            proxyToClientOs.write("\r\n".getBytes(StandardCharsets.UTF_8));

            // wrap client socket
            FileInputStream is = new FileInputStream(certFile);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(is, "secret".toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keyStore, "secret".toCharArray());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(
                    clientSocket,
                    clientSocket.getInetAddress().getHostAddress(),
                    clientSocket.getPort(),
                    true);

            sslSocket.setUseClientMode(false);
            sslSocket.setNeedClientAuth(false);
            sslSocket.setWantClientAuth(false);
            sslSocket.addHandshakeCompletedListener(
                    (HandshakeCompletedEvent handshakeCompletedEvent) -> {
                        try {
                            SSLSocket httpsClientSocket = handshakeCompletedEvent.getSocket();
                            String connType = rawHttpRequest.getHeaderParam("Proxy-Connection", "");

                            if ("keep-alive".equals(connType.toLowerCase(Locale.ROOT))) {
                                httpsClientSocket.setKeepAlive(true);
                            }

                            if ("close".equalsIgnoreCase(connType)) {
                                httpsClientSocket.close();
                            } else {
                                this.processRequest(httpsClientSocket);
                            }
                            log.info("DebugAgent: https finished with socket status {} [{}]", httpsClientSocket.isClosed(), id);

                        } catch (Throwable e) {
                            log.error("DebugAgent: exception [{" + id + " }]", e);
                            log.error("DebugAgent: Error in handshake callback " + rawHttpRequest.getUrl().getHost() + " : " + e);
                            this.close();
                        } finally {
                            synchronized (this.listClientSocket) {
                                this.listClientSocket.notifyAll();
                            }
                        }
                    });
            sslSocket.startHandshake();
            log.info("DebugAgent: https finished handshake [{}]", id);

        } catch (Throwable e) {
            log.error("DebugAgent: exception", e);
            log.error("DebugAgent: * DO CONNECT EXCEPTION *: " + rawHttpRequest.getUrl().getHost() + " : " + e);
            this.close();

            synchronized (this.listClientSocket) {
                this.listClientSocket.notifyAll();
            }
        }
    }

    @Override
    public void close() {
        log.info("DebugAgent: close proxy connect by thread {} [{}]", Thread.currentThread().getId(), id);

        for (Socket socket : this.listClientSocket) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception e) {
                log.error("DebugAgent: close socket failed " + e.getMessage(), e);
            }
        }
    }
}



