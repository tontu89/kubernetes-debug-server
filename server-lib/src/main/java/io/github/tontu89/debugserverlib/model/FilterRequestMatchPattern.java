package io.github.tontu89.debugserverlib.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.github.tontu89.debugserverlib.utils.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class FilterRequestMatchPattern implements Serializable {
    private String jsonPath;

    private String matchPattern;

    @JsonIgnore
    private transient JsonPath jsonPathObject;

    @JsonIgnore
    private transient Pattern matchPatternObject;

    @Builder
    public FilterRequestMatchPattern(String jsonPath, String matchPattern) {
        this.jsonPath = jsonPath;
        this.matchPattern = matchPattern;
        this.init();
    }

    public void init() {
        this.jsonPathObject = JsonPath.compile(this.jsonPath);
        this.matchPatternObject = Pattern.compile(this.matchPattern);
    }

    public boolean isMatch(DocumentContext httpRequestJsonFormat) {
        boolean matchResult = false;
        try {
            String data = httpRequestJsonFormat.read(this.jsonPathObject, String.class);
            if (data != null) {
                Matcher matcher = this.matchPatternObject.matcher(data);
                matchResult = matcher.matches();
            }

            log.debug("DebugLib: URI data [{}] with matchPattern [{}] with result {}", data, matchPattern, matchResult);

        } catch (Exception e) {
            log.error(Constants.LOG_ERROR_PREFIX + e.getMessage(), e);
        }
        return matchResult;
    }
}
