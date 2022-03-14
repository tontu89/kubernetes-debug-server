package io.github.tontu89.debugserverlib.model;

import io.github.tontu89.debugserverlib.utils.DebugUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.IOException;
import java.io.Serializable;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse implements Serializable {
    private int status;
    private String dataBase64;

    public void encodeDataBase64(Object o) throws IOException {
        if (o != null) {
            dataBase64 = DebugUtils.objectToBase64String(o);
        }
    }
}
