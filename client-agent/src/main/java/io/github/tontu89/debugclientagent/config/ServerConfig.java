package io.github.tontu89.debugclientagent.config;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ToString
public class ServerConfig {
    private Map<String, String> filesMapping;
    private InetSocketAddress inetSocketAddress;
    private Map<String, String> environments;
    private Map<String, String> systemProperties;
    private String address;

    public InetSocketAddress getServerInetAddress() {
        if (this.inetSocketAddress == null) {
            this.inetSocketAddress = Optional.ofNullable(address).map(ServerConfig::stringToInetSocketAddress).orElse(null);
        }
        return this.inetSocketAddress;
    }

    private static InetSocketAddress stringToInetSocketAddress(String address) {
        String[] parts = address.split(":");
        return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
    }

    public Map<String, String> getFilesMapping() {
        return filesMapping;
    }

    public void setFilesMapping(Map<String, String> filesMapping) {
        this.filesMapping = filesMapping;
    }

    public Map<String, String> getEnvironments() {
        return environments;
    }

    public void setEnvironments(Map<String, String> environments) {
        this.environments = environments;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public void setSystemProperties(Map<String, String> systemProperties) {
        this.systemProperties = systemProperties;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
