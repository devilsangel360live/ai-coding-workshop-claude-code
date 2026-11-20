import com.example.taskmanager.infrastructure.legacy.LegacyTaskProcessor;
import com.example.taskmanager.infrastructure.legacy.TaskOutputWriter;
import com.example.taskmanager.infrastructure.legacy.TransformType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LegacyTaskProcessorLoggingTest {

    @Mock
    private TaskOutputWriter outputWriter;

    private LegacyTaskProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new LegacyTaskProcessor(outputWriter);
    }

    @Test
    void processTask_formatForExport_writesResultThroughOutputWriter() throws IOException {
        var result = processor.processTask(993_001, "Hello World", TransformType.FORMAT, true);

        verify(outputWriter, times(1)).write(993_001, "hELLO_wORLD");
        assertEquals("hELLO_wORLD", result);
    }

    @Test
    void processTask_whenWriterThrows_logsWarningWithTaskIdAndStillReturnsResult(CapturedOutput output) throws IOException {
        doThrow(new IOException("disk full")).when(outputWriter).write(993_002, "hELLO_wORLD");

        var result = processor.processTask(993_002, "Hello World", TransformType.FORMAT, true);

        assertEquals("hELLO_wORLD", result);
        assertEquals(1, processor.getProcessedCount());
        assertTrue(output.getAll().contains("Failed to write output for task 993002"), output.getAll());
        assertTrue(output.getAll().contains("disk full"), output.getAll());
    }
}
