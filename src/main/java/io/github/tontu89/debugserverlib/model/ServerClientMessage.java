package io.github.tontu89.debugserverlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@ToString
public class ServerClientMessage implements Serializable {
    public enum Type {REQUEST, RESPONSE}

    private MessageRequest request;
    private MessageResponse response;
    private String id;
    private Type type;
}
