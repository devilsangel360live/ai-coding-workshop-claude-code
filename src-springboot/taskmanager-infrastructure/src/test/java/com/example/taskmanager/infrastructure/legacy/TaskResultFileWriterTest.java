package com.example.taskmanager.infrastructure.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskResultFileWriterTest {

    private static final int ID = 992_001;
    private static final Path OUTPUT_FILE = Path.of("task_" + ID + ".txt");

    private final TaskResultFileWriter writer = new TaskResultFileWriter();

    @AfterEach
    void deleteOutputFile() throws IOException {
        Files.deleteIfExists(OUTPUT_FILE);
    }

    @Test
    void pathFor_id_returnsTaskFileInWorkingDirectory() {
        var path = TaskResultFileWriter.pathFor(ID);

        assertEquals(OUTPUT_FILE, path);
    }

    @Test
    void write_newId_writesContentToTaskFileInWorkingDirectory() throws IOException {
        writer.write(ID, "hELLO_wORLD");

        assertEquals("hELLO_wORLD", Files.readString(OUTPUT_FILE));
    }

    @Test
    void write_existingFile_overwritesIt() throws IOException {
        Files.writeString(OUTPUT_FILE, "old content that is much longer");

        writer.write(ID, "new");

        assertEquals("new", Files.readString(OUTPUT_FILE));
    }

    @Test
    void write_loneSurrogate_throwsIOExceptionAndWritesNoFile() {
        var loneHighSurrogate = "abc" + "😀".charAt(0);

        assertThrows(IOException.class, () -> writer.write(ID, loneHighSurrogate));

        assertFalse(Files.exists(OUTPUT_FILE));
    }

    @Test
    void write_pathIsADirectory_throwsIOException() throws IOException {
        Files.createDirectory(OUTPUT_FILE);

        assertThrows(IOException.class, () -> writer.write(ID, "content"));
    }
}
