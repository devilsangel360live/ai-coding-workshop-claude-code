package com.example.taskmanager.infrastructure.legacy;

import java.io.IOException;

/**
 * Persists the formatted output of a legacy task.
 */
public interface TaskOutputWriter {

    void write(int id, String content) throws IOException;
}
