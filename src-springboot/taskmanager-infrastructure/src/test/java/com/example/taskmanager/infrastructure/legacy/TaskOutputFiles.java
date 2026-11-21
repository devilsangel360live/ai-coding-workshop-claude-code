package com.example.taskmanager.infrastructure.legacy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the task output files a test expects in the working directory and deletes them afterwards.
 */
final class TaskOutputFiles {

    private final List<Path> trackedPaths = new ArrayList<>();

    /** Deletes any leftover output file for this id and registers it for cleanup. */
    Path track(int id) throws IOException {
        var path = TaskResultFileWriter.pathFor(id);
        Files.deleteIfExists(path);
        trackedPaths.add(path);
        return path;
    }

    void deleteAll() throws IOException {
        for (var path : trackedPaths) {
            Files.deleteIfExists(path);
        }
    }
}
