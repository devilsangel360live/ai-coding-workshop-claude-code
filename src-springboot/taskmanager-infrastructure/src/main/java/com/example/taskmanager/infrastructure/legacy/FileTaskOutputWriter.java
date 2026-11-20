package com.example.taskmanager.infrastructure.legacy;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes task output to {@code task_<id>.txt} in the working directory, overwriting any existing file.
 * Throws on content that isn't valid UTF-16 (for example a lone surrogate) rather than writing a replacement character.
 */
@Component
public class FileTaskOutputWriter implements TaskOutputWriter {

    @Override
    public void write(int id, String content) throws IOException {
        Files.writeString(Path.of("task_" + id + ".txt"), content);
    }
}
