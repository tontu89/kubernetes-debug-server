package io.github.tontu89.debugserverlib.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

import static io.github.tontu89.debugserverlib.utils.Constants.OBJECT_MAPPER;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class MessageRequest implements Serializable {
    public enum Command {
        SERVER_GET_ENV, SERVER_GET_PROP, SERVER_EXIT, SERVER_ADD_FILTER_PATTERN, SERVER_CLEAR_ALL_FILTER_PATTERN,
        SERVER_GET_ALL_FILTER_PATTERN, SERVER_EXECUTE_HTTP_REQUEST, SERVER_DOWNLOAD_FILE,
        CLIENT_EXECUTE_HTTP_REQUEST, HEART_BEAT
    }

    private Command command;

    private byte[] data;

    public void setData(Object o) throws JsonProcessingException {
        if (o != null) {
            data = OBJECT_MAPPER.writeValueAsBytes(o);
        }
    }
}
