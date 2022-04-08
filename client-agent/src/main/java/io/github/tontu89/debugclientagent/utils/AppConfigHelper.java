package io.github.tontu89.debugclientagent.utils;

import io.github.tontu89.debugclientagent.config.AppConfig;
import io.github.tontu89.debugclientagent.config.LocalConfig;
import io.github.tontu89.debugclientagent.config.ServerConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.tontu89.debugserverlib.model.FilterRequestMatchPattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Slf4j
@Data
public class AppConfigHelper {
    private AppConfig appConfig;
    private File configFilePath;
    private JsonNode configFile;
    private JsonNode configFileProfileNode;
    private ObjectMapper objectMapper;
    private String profileName;

    public AppConfigHelper(String configFilePath, String profileName) throws Exception {
        this.appConfig = init(configFilePath, profileName);
    }

    public AppConfigHelper(String agentArgs) throws Exception {
        if (!StringUtils.isBlank(agentArgs)) {
            String[] argParts = agentArgs.split("#");
            if (argParts.length == 1) {
                this.appConfig = init(argParts[0], null);
            } else if (argParts.length == 2) {
                this.appConfig = init(argParts[0], argParts[1]);
            }
        }
        if (this.appConfig == null) {
            throw new Exception("Invalid parameter format. It should be CONFIG_FILE_PATH#DEFAULT_PROFILE_ID");
        }
    }

    private AppConfig init(String configFilePathString, String profileNameArgs) throws Exception {
        this.configFilePath = new File(configFilePathString);
        this.profileName = profileNameArgs;

        if (!configFilePath.exists()) {
            log.error("DebugAgent: {} does not exist", configFilePathString);
            throw new FileNotFoundException();
        }

        if (!configFilePath.isFile()) {
            log.error("DebugAgent: {} not a config file", configFilePathString);
            throw new Exception("Not a config file");
        }

        try {
            this.objectMapper = new ObjectMapper(new YAMLFactory());
            this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
            this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

            this.configFile = this.objectMapper.readTree(configFilePath);
            JsonNode allProfilesNode = this.configFile.get("profiles");

            if (StringUtils.isBlank(this.profileName)) {
                this.profileName = allProfilesNode.fieldNames().next();
            }

            this.configFileProfileNode = allProfilesNode.path(this.profileName);

            if (this.configFileProfileNode == null || this.configFileProfileNode.isNull() || this.configFileProfileNode.isEmpty()) {
                throw new Exception("Invalid config profile " + this.profileName);
            }

            AppConfig appConfig = this.objectMapper.treeToValue(this.configFileProfileNode, AppConfig.class);

            if (StringUtils.isBlank(appConfig.getClientName())) {
                throw new Exception("Missing config for " + this.profileName + ".client-name");
            }

            if (appConfig.getFilters() == null || appConfig.getFilters().isEmpty()) {
                throw new Exception("Missing config for " + this.profileName + ".filters");
            }

            for (FilterRequestMatchPattern f : appConfig.getFilters()) {
                try {
                    f.init();
                } catch (Throwable e) {
                    log.error("DebugAgent: Invalid filter syntax for json-path: {}, match-pattern: {}", f.getJsonPath(), f.getMatchPattern());
                    throw e;
                }
            }

            this.validateLocalInfo(appConfig.getLocal());

            for (String serverName : appConfig.getServers().keySet()) {
                this.validateServerInfo(serverName, appConfig.getServers().get(serverName));
            }

            return appConfig;

        } catch (Throwable e) {
            log.error("DebugAgent: cannot parse config", configFilePathString);
            log.error("DebugAgent: exception", e);
            throw e;
        }
    }

    private void validateServerInfo(String serverName, ServerConfig serverInfo) throws Exception {
        if (serverInfo == null) {
            throw new Exception("Missing config for " + this.profileName + ".servers");
        }

        if (StringUtils.isBlank(serverInfo.getAddress())) {
            throw new Exception("Missing config for " + this.profileName + ".servers." + serverName + ".address");
        }

        if (serverInfo.getServerInetAddress() == null) {
            throw new Exception("Missing config for " + this.profileName + ".servers." + serverName + "].addresses");
        }
    }

    private void validateLocalInfo(LocalConfig localInfo) throws Exception {
        if (localInfo == null) {
            throw new Exception("Missing config for " + this.profileName + ".local");
        }

        if (localInfo.getProxyPort() == null) {
            throw new Exception("Missing config for " + this.profileName + ".local.proxy-port");
        }

        if (StringUtils.isBlank(localInfo.getWebUrl())) {
            throw new Exception("Missing config for " + this.profileName + ".local.web-url");
        }

        if (localInfo.getEnvironments() != null) {
            localInfo.setEnvironments(new TreeMap<>(localInfo.getEnvironments()));
        }

        if (localInfo.getSystemProperties() != null) {
            localInfo.setSystemProperties(new TreeMap<>(localInfo.getSystemProperties()));
        }
    }

    public void updateServerInfoEnvironments(String serverName, Map<String, String> environments) throws IOException {
        if (environments == null) {
            return;
        }
        environments = new TreeMap<>(environments);

        this.appConfig.getServers().get(serverName).setEnvironments(environments);

        ((ObjectNode)this.configFileProfileNode.get("servers").get(serverName)).putPOJO("environments", environments);

        this.objectMapper.writeValue(this.configFilePath, this.configFile);
    }

    public void updateServerInfoSystemProperties(String serverName, Map<String, String> systemProperties) throws IOException {
        if (systemProperties == null) {
            return;
        }
        systemProperties = new TreeMap<>(systemProperties);

        this.appConfig.getServers().get(serverName).setSystemProperties(systemProperties);

        ((ObjectNode)this.configFileProfileNode.get("servers").get(serverName)).putPOJO("system-properties", systemProperties);

        this.objectMapper.writeValue(this.configFilePath, this.configFile);
    }

    public Map<String, String> getFinalEnvironments() {
        Map<String, String> finalEnvironments = new HashMap<>();

        this.appConfig.getServers().forEach((serverName, serverInfo) -> finalEnvironments.putAll(serverInfo.getEnvironments()));

        buildFinalProps(finalEnvironments, this.appConfig.getLocal().getEnvironments());

        return finalEnvironments;
    }

    public Map<String, String> getFinalSystemProperties() {
        Map<String, String> finalSystemProperties = new HashMap<>();

        this.appConfig.getServers().forEach((serverName, serverInfo) -> finalSystemProperties.putAll(serverInfo.getSystemProperties()));

        buildFinalProps(finalSystemProperties, this.appConfig.getLocal().getSystemProperties());

        return finalSystemProperties;
    }

    private void buildFinalProps(Map<String, String> base, Map<String, String> additional) {
        Optional.ofNullable(additional).ifPresent(add -> add.forEach((key, value) -> {
            if (value == null) {
                base.remove(key);
            } else {
                base.put(key, value);
            }
        }));
    }
}
