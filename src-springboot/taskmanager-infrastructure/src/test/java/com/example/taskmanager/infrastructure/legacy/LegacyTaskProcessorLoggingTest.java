package com.example.taskmanager.infrastructure.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LegacyTaskProcessorLoggingTest {

    private static final int TYPE_FORMAT = 1;

    @Mock
    private TaskOutputWriter outputWriter;

    private LegacyTaskProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new LegacyTaskProcessor(outputWriter);
    }

    @Test
    void processTask_formatForExport_writesResultThroughOutputWriter() throws IOException {
        var result = processor.processTask(993_001, "Hello World", TYPE_FORMAT, true);

        verify(outputWriter, times(1)).write(993_001, "hELLO_wORLD");
        assertEquals("hELLO_wORLD", result);
    }

    @Test
    void processTask_whenWriterThrows_logsWarningWithTaskIdAndStillReturnsResult(CapturedOutput output) throws IOException {
        doThrow(new IOException("disk full")).when(outputWriter).write(993_002, "hELLO_wORLD");

        var result = processor.processTask(993_002, "Hello World", TYPE_FORMAT, true);

        assertEquals("hELLO_wORLD", result);
        assertEquals(1, processor.getProcessedCount());
        assertTrue(output.getAll().contains("Failed to write output for task 993002"), output.getAll());
        assertTrue(output.getAll().contains("disk full"), output.getAll());
    }

    @Test
    void processTask_whenInterruptedDuringWriteDelay_logsWarningWithTaskIdAndStillWrites(CapturedOutput output) throws IOException {
        Thread.currentThread().interrupt();

        try {
            var result = processor.processTask(993_003, "Hello World", TYPE_FORMAT, true);

            assertEquals("hELLO_wORLD", result);
            assertFalse(Thread.currentThread().isInterrupted());
            verify(outputWriter, times(1)).write(993_003, "hELLO_wORLD");
            assertTrue(output.getAll().contains("Interrupted while waiting to write output for task 993003"), output.getAll());
        } finally {
            Thread.interrupted();
        }
    }
}
