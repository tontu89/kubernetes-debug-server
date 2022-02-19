package com.kubernetes.debugserver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@ToString
public class ServerClientMessage {
    public enum Type {REQUEST, RESPONSE}

    private MessageRequest request;
    private MessageResponse response;
    private String id;
    private Type type;
}
