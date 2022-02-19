package com.kubernetes.debugserver.filter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.JsonPath;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class FilterRequestMatchPattern {
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
        Matcher matcher = this.matchPatternObject.matcher(data);
        return matcher.matches();
    }

    private String getJsonPathData(String json) {
        return JsonPath.parse(json).<String>read(this.jsonPathObject);
    }


}
