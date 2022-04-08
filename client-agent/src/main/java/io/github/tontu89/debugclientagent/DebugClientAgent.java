package io.github.tontu89.debugclientagent;

import io.github.tontu89.debugclientagent.config.AppConfig;
import io.github.tontu89.debugclientagent.config.ServerConfig;
import io.github.tontu89.debugclientagent.proxy.ProxyServer;
import io.github.tontu89.debugclientagent.utils.AppConfigHelper;
import io.github.tontu89.debugclientagent.utils.DebugServerHelper;
import io.github.tontu89.debugclientagent.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@Slf4j
public class DebugClientAgent {
    private static Boolean stop = false;
    private static Boolean run = false;
    private static Executor executor = Executors.newFixedThreadPool(1);
    private static Future<Void> mainJob = null;


    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        try {
            AppConfigHelper appConfigHelper = new AppConfigHelper(agentArgs);
            AppConfig appConfig = appConfigHelper.getAppConfig();

//            System.setProperty("javax.net.ssl.trustStore", "/Users/linh/tmp/web_debug/spring-boot-jwt-without-JPA/DebugAgentClient/src/main/resources/CA/myTrustStore");
//            System.setProperty("javax.net.ssl.trustStorePassword", "myTrustStore");
            SecurityUtils.setupTrustStore();
            SecurityUtils.setupProxy(appConfig.getLocal().getProxyPort() + "");

            mainJob = CompletableFuture.runAsync(() -> {

                try {
                    List<DebugServerCommunication> servers = new ArrayList<>();

                    for (String serverName : appConfig.getServers().keySet()) {
                        ServerConfig serverConfig = appConfig.getServers().get(serverName);

                        try {
                            DebugServerCommunication debugServer = new DebugServerCommunication(appConfig.getClientName(),
                                    serverConfig.getServerInetAddress().getAddress().getHostAddress(),
                                    serverConfig.getServerInetAddress().getPort(),
                                    appConfig.getLocal().getWebUrl());

                            downloadFiles(appConfigHelper, serverName, debugServer);
                            applyEnvironment(appConfigHelper, serverName, debugServer);
                            applySystemProperties(appConfigHelper, serverName, debugServer);
                            applyFilterRequest(appConfigHelper, debugServer);

                            servers.add(debugServer);
                        } catch (Exception e) {
                            log.error("DebugAgent: Cannot connect to {} on port {}", serverConfig.getServerInetAddress().getAddress().getHostAddress(), serverConfig.getServerInetAddress().getPort());
                            log.error("DebugAgent: exception " + e.getMessage(), e);
                            break;
                        }
                    }

                    if (!servers.isEmpty()) {
                        DebugServerHelper debugServerHelper = new DebugServerHelper(servers);

                        ProxyServer proxyServer = new ProxyServer(appConfig.getLocal().getProxyPort(), debugServerHelper);
                        proxyServer.start();

                        synchronized (run) {
                            run.notifyAll();
                        }
                        run = true;

                        while (proxyServer.isRunning() && debugServerHelper.isRunning() && !stop) {
                            debugServerHelper.waitToClose(1000);
                        }
                    }
                } catch (Throwable e) {
                    log.error("DebugAgent: exception " + e.getMessage(), e);
                }
                log.info("DebugAgent: Quit Agent");

                CompletableFuture.runAsync(() -> System.exit(-1), executor);

            }, executor);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("DebugAgent: Stopping Agent");
                stop = true;
                while (!mainJob.isDone()) {
                    try {
                        mainJob.get(500, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException | ExecutionException e) {
                        log.error("DebugAgent: exception " + e.getMessage(), e);
                    } catch (TimeoutException e) {

                    }
                }
                log.info("DebugAgent: Agent is stopped");
            }));

            // Wait server up
            synchronized (run) {
                try {
                    run.wait(60 * 1000);
                    if (!run) {
                        log.error("DebugAgent: timeout");
                        throw new TimeoutException();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            System.out.println("============AGENT LOADED==============");
        } catch (Throwable e) {
            log.error("DebugAgent: exception", e);
            System.exit(-1);
        }
    }

    public static void main(String[] args) throws Exception {
        premain(null, null);
    }

    private static void applyEnvironment(AppConfigHelper appConfigHelper, String serverName, DebugServerCommunication debugServer) throws Exception {
        appConfigHelper.updateServerInfoEnvironments(serverName, debugServer.getServerEnvironments());
        SecurityUtils.setEnvironments(appConfigHelper.getFinalEnvironments());
    }

    private static void applySystemProperties(AppConfigHelper appConfigHelper, String serverName, DebugServerCommunication debugServer) throws Exception {
        appConfigHelper.updateServerInfoSystemProperties(serverName, debugServer.getServerSystemProperties());
        SecurityUtils.setSystemProperties(appConfigHelper.getFinalSystemProperties());
    }

    private static void applyFilterRequest(AppConfigHelper appConfigHelper, DebugServerCommunication debugServer) throws Exception {
        debugServer.addRequestFilter(appConfigHelper.getAppConfig().getFilters());
    }

    private static void downloadFiles(AppConfigHelper appConfigHelper, String serverName, DebugServerCommunication debugServerCommunication) throws Exception {
        AppConfig appConfig = appConfigHelper.getAppConfig();
        Map<String, String> filesMapping = appConfig.getServers().get(serverName).getFilesMapping();

        if (filesMapping != null) {
            for(String serverFilePath: filesMapping.keySet()) {
                try {
                    debugServerCommunication.downloadFile(serverFilePath, filesMapping.get(serverFilePath));
                } catch (Exception e) {
                    log.error("DebugAgent: cannot download file from server " + serverFilePath + " to local " + filesMapping.get(serverFilePath) + " because of " + e.getMessage(), e);
                    throw e;
                }
            }
        }
    }
}
