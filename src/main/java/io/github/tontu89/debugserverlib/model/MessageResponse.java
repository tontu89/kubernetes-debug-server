package io.github.tontu89.debugserverlib.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

import java.io.Serializable;

import static io.github.tontu89.debugserverlib.utils.Constants.OBJECT_MAPPER;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse implements Serializable {
    private int status;
    private byte[] data;

    public void setData(Object o) throws JsonProcessingException {
        if (o != null) {
            data = OBJECT_MAPPER.writeValueAsBytes(o);
        }
    }
}
