package io.github.tontu89.debugclientagent.config;

import io.github.tontu89.debugserverlib.model.FilterRequestMatchPattern;

import java.util.List;

public class MockServerConfig {
    private Boolean enable;
    private List<FilterRequestMatchPattern> filters;
}
