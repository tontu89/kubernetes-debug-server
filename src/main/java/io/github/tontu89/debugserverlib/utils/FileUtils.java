package io.github.tontu89.debugserverlib.utils;

import io.github.tontu89.debugserverlib.model.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Slf4j
public class FileUtils {

    public static MessageResponse downloadFile(String filePath) {
        MessageResponse.MessageResponseBuilder builder = MessageResponse.builder();

        if (StringUtils.isBlank(filePath)) {
            builder.status(HttpStatus.NOT_FOUND.value());
        } else {
            File file = new File(filePath);

            if (!file.exists()) {
                builder.status(HttpStatus.NO_CONTENT.value())
                        .data((filePath + " does not exist").getBytes(StandardCharsets.UTF_8));
            } else if (!file.isFile()) {
                builder.status(HttpStatus.NO_CONTENT.value())
                        .data((filePath + " not a file").getBytes(StandardCharsets.UTF_8));
            } else {
                try {
                    builder.data(Files.readAllBytes(file.toPath()))
                            .status(HttpStatus.OK.value());
                } catch (Exception e) {
                    log.debug(Constants.LOG_ERROR_PREFIX + e.getMessage(), e);
                    builder.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .data(e.getMessage().getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        return builder.build();
    }
}
