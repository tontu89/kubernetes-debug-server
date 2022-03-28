package io.github.tontu89.debugserverlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HttpResponseInfo implements Serializable {
    private int httpStatus;
    private Map<String, String> headers;
    private String payload;

    public static void removeEncodingHeader(Map<String, String> headers) {
        Optional.ofNullable(headers).ifPresent(tmpHeaders -> tmpHeaders.entrySet()
                .removeIf(header ->
                        "transfer-encoding".equals(header.getKey().toLowerCase(Locale.ROOT)) ||
                                "content-encoding".equals(header.getKey().toLowerCase(Locale.ROOT))));
    }

    public void removeEncodingHeader() {
        HttpResponseInfo.removeEncodingHeader(this.headers);
    }
}
