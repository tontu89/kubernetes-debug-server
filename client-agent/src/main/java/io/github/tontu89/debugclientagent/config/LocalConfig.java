package io.github.tontu89.debugclientagent.config;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Data
@ToString
public class LocalConfig {
    private Integer proxyPort;
    private String webUrl;
    private Map<String, String> environments;
    private Map<String, String> systemProperties;
}
