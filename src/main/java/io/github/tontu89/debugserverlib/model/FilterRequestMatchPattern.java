package io.github.tontu89.debugserverlib.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.JsonPath;
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
    private JsonPath jsonPathObject;

    @JsonIgnore
    private Pattern matchPatternObject;

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

    @SneakyThrows
    public boolean isMatch(String httpRequestJsonFormat) {
        String data = this.getJsonPathData(httpRequestJsonFormat);
        boolean matchResult = false;
        if (data != null) {
            Matcher matcher = this.matchPatternObject.matcher(data);
            matchResult = matcher.matches();
        }

        log.debug("DebugLib: URI data [{}] with matchPattern [{}] with result {}", data, matchPattern, matchResult);

        return matchResult;
    }

    private String getJsonPathData(String json) {
        return JsonPath.parse(json).read(this.jsonPathObject, String.class);
    }
}
