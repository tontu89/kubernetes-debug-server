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
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class MessageRequest implements Serializable {
    public enum Command {
        SERVER_GET_ENV, SERVER_GET_PROP, SERVER_EXIT, SERVER_ADD_FILTER_PATTERN, SERVER_CLEAR_ALL_FILTER_PATTERN,
        SERVER_GET_ALL_FILTER_PATTERN, SERVER_EXECUTE_HTTP_REQUEST, SERVER_DOWNLOAD_FILE, SERVER_SET_CLIENT_NAME,
        CLIENT_EXECUTE_HTTP_REQUEST, HEART_BEAT
    }

    private Command command;

    private String dataBase64;

    public void encodeDataBase64(Object o) throws IOException {
        if (o != null) {
            dataBase64 = DebugUtils.objectToBase64String(o);
        }
    }
}
