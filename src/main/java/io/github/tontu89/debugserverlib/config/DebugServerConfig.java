package io.github.tontu89.debugserverlib.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration()
@ConfigurationProperties("debug-server")
@PropertySource(value = "classpath:application.yaml", factory = YamlPropertySourceFactory.class)
@Data
@ToString
public class DebugServerConfig {
    private int port;
    private int numberOfThreadForClientRequestProcessing;
}
