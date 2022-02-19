package io.github.tontu89.debugserverlib.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.tontu89.debugserverlib.model.ServerClientMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
public class DebugUtils {
    @SneakyThrows
    public static <T> T escapedJsonStringToObject(String escapedJsonString, Class<T> clazz) {
        return Constants.OBJECT_MAPPER.readValue(unescapeJsonString(escapedJsonString), clazz);
    }

    @SneakyThrows
    public static <T> T escapedJsonStringToObject(String escapedJsonString, TypeReference<T> clazz) {
        return Constants.OBJECT_MAPPER.readValue(unescapeJsonString(escapedJsonString), clazz);
    }

    public static String unescapeJsonString(String escapedJsonString) {
        String unescapeJsonString = StringEscapeUtils.unescapeJson(escapedJsonString);
        unescapeJsonString = unescapeJsonString.startsWith("\"") ? unescapeJsonString.substring(1) : unescapeJsonString;
        return unescapeJsonString.endsWith("\"") ? unescapeJsonString.substring(0, unescapeJsonString.length() - 1) : unescapeJsonString;
    }

    public static void writeMessage(DataOutputStream dos, ServerClientMessage message) throws IOException {
        byte[] messageInByte = Constants.OBJECT_MAPPER.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);

        dos.writeInt(messageInByte.length);

        for (int i = 0; i < messageInByte.length; i = i + Constants.MESSAGE_CHUNK_SIZE_IN_BYTE) {
            dos.write(messageInByte, i, (i + Constants.MESSAGE_CHUNK_SIZE_IN_BYTE) > messageInByte.length ? (messageInByte.length - i) : Constants.MESSAGE_CHUNK_SIZE_IN_BYTE);
        }
        dos.flush();
    }

    public static ServerClientMessage readMessage(DataInputStream dis) throws IOException {
        byte[] data = new byte[dis.readInt()];

        for (int i = 0, j = 0, chunkSize = Constants.MESSAGE_CHUNK_SIZE_IN_BYTE; i < data.length; i = i + j) {
            if (i + chunkSize > data.length) {
                chunkSize = data.length - i;
            }
            j = dis.read(data, i, chunkSize);
        }

        String dataInString = new String(data, Charset.forName("UTF-8"));

        log.debug("DebugLib: Read DIS: message [{}]", dataInString);

        return Constants.OBJECT_MAPPER.readValue(dataInString, ServerClientMessage.class);
    }
}
