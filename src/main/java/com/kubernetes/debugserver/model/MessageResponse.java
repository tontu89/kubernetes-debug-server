package com.kubernetes.debugserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

import static com.kubernetes.debugserver.utils.Constants.OBJECT_MAPPER;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {
    private int status;
    private String data;

    @SneakyThrows
    public void setData(Object data) {
        this.data = OBJECT_MAPPER.writeValueAsString(data);
    }
}
