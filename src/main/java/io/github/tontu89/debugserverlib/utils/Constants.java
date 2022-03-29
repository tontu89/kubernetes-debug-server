package io.github.tontu89.debugserverlib.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Constants {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final int POLL_QUEUE_TIMEOUT_MS = 200;
    public static final int MESSAGE_CHUNK_SIZE_IN_BYTE = 10;
    public static final int MAX_REQUEST_TIME_OUT_MS = 10 * 60 * 1000;
    public static final int HEART_BEAT_RESPONSE_CODE = 222;
    public static final String LOG_ERROR_PREFIX = "DebugLib: exception ";
    public static final String SPRING_PROFILE_NAME = "RemoteDebug";
}
