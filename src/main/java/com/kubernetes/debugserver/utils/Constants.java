package com.kubernetes.debugserver.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Constants {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final int POLL_QUEUE_TIMEOUT_MS = 200;
    public static final int MESSAGE_CHUNK_SIZE_IN_BYTE = 10;
    public static final char START_MESSAGE_INDICATOR = 2;
    public static final char END_MESSAGE_INDICATOR = 3;
}
