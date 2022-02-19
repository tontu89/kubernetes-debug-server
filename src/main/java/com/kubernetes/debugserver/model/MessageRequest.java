package com.kubernetes.debugserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class MessageRequest implements Serializable {
    public enum Command {
        SERVER_GET_ENV, SERVER_GET_PROP, SERVER_EXIT, SERVER_ADD_FILTER_PATTERN, SERVER_CLEAR_ALL_FILTER_PATTERN,
        SERVER_GET_ALL_FILTER_PATTERN, SERVER_EXECUTE_HTTP_REQUEST,
        CLIENT_EXECUTE_HTTP_REQUEST
    }

    private Command command;

    private String data;

}
