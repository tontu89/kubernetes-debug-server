package io.github.tontu89.debugclientagent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.tontu89.debugserverlib.model.FilterRequestMatchPattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {
    private List<FilterRequestMatchPattern> filters;
    private Map<String, ServerConfig> servers;
    private LocalConfig local;
    private String clientName;

}
