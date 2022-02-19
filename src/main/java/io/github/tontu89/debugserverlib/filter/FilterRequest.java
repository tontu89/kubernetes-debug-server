package io.github.tontu89.debugserverlib.filter;

import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@ToString
public class FilterRequest {
    private List<FilterRequestMatchPattern> matchPatterns;

    public FilterRequest() {
        this.matchPatterns = new ArrayList<>();
    }

    public void addPattern(FilterRequestMatchPattern debugServerRequestMatchPattern) {
        if (debugServerRequestMatchPattern != null && debugServerRequestMatchPattern.getJsonPathObject() != null && debugServerRequestMatchPattern.getMatchPatternObject() != null) {
            this.matchPatterns.add(debugServerRequestMatchPattern);
        }
    }

    public void addPattern(List<FilterRequestMatchPattern> debugServerRequestMatchPattern) {
        debugServerRequestMatchPattern.stream().forEach(e -> addPattern(e));
    }

    public boolean isMatch(String httpRequestJsonFormat) {
        for(FilterRequestMatchPattern matchPattern : this.matchPatterns) {
            if (matchPattern.isMatch(httpRequestJsonFormat)) {
                return true;
            }
        }
        return false;
    }

}
