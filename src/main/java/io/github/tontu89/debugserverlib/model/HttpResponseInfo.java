package io.github.tontu89.debugserverlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HttpResponseInfo {
    private int httpStatus;
    private Map<String, String> headers;
    private String payload;

    public void resetContentLengthHeader() {
        int contentLength = 0;

        if (this.payload != null) {
            contentLength = this.payload.getBytes(StandardCharsets.UTF_8).length;
        }

        if (this.headers != null) {
            for (String key : this.headers.keySet()) {
                if ("content-length".equals(key.toLowerCase(Locale.ROOT))) {
                    this.headers.remove(key);
                    break;
                }
            }

            if (contentLength > 0) {
                this.headers.put("Content-Length", contentLength + "");
            }
        }
    }
}
