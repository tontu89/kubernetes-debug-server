package io.github.tontu89.debugserverlib.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;

import static io.github.tontu89.debugserverlib.utils.Constants.SPRING_PROFILE_NAME;

@Profile(SPRING_PROFILE_NAME)
@ComponentScan("io.github.tontu89.debugserverlib")
public class EnableRemoteDebugServerConfig {
}
