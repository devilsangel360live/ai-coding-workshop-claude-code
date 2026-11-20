import com.example.taskmanager.infrastructure.legacy.LegacyBatchImportService;
import com.example.taskmanager.infrastructure.legacy.LegacyBatchImportService.ImportRecord;
import com.example.taskmanager.infrastructure.legacy.FileTaskOutputWriter;
import com.example.taskmanager.infrastructure.legacy.LegacyTaskProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for LegacyBatchImportService, wired to a real LegacyTaskProcessor
 * so the tests pin the combined behavior the refactor must preserve.
 */
@DisplayName("LegacyBatchImportService characterization tests")
class LegacyBatchImportServiceTest {

    private static final int TYPE_FORMAT = 1;
    private static final int TYPE_SENTENCE = 2;
    private static final int TYPE_OTHER = 3;

    private LegacyTaskProcessor processor;
    private LegacyBatchImportService service;
    private final List<Path> createdPaths = new ArrayList<>();

    @BeforeEach
    void setUp() {
        processor = new LegacyTaskProcessor(new FileTaskOutputWriter());
        service = new LegacyBatchImportService(processor);
    }

    @AfterEach
    void deleteCreatedFiles() throws IOException {
        for (var path : createdPaths) {
            Files.deleteIfExists(path);
        }
    }

    private Path outputFileFor(int id) throws IOException {
        var path = Path.of("task_" + id + ".txt");
        Files.deleteIfExists(path);
        createdPaths.add(path);
        return path;
    }

    @Test
    void importBatch_validRecords_processesEachWithFormatFlagInOrder() throws IOException {
        outputFileFor(991_001);
        var records = List.of(
                new ImportRecord(991_001, "Hello World", TYPE_FORMAT),
                new ImportRecord(991_002, "Hello World", TYPE_SENTENCE));

        var results = service.importBatch(records);

        assertEquals(List.of("hELLO_wORLD", "Hello world"), results);
        assertEquals(2, service.getProcessedCount());
        assertEquals("Hello world", service.getLastProcessed());
    }

    @Test
    void importBatch_typeOneRecord_writesFile() throws IOException {
        var file = outputFileFor(991_003);

        service.importBatch(List.of(new ImportRecord(991_003, "Export Me", TYPE_FORMAT)));

        assertEquals("eXPORT_mE", Files.readString(file));
    }

    @Test
    void importBatch_typeTwoRecord_writesNoFile() throws IOException {
        var file = outputFileFor(991_004);

        service.importBatch(List.of(new ImportRecord(991_004, "No File", TYPE_SENTENCE)));

        assertFalse(Files.exists(file));
    }

    @Test
    void importBatch_invalidRecords_silentlyDropsThemWithoutCounting() {
        var records = List.of(
                new ImportRecord(991_010, "Kept Record", TYPE_SENTENCE),
                new ImportRecord(991_011, "unsupported type", TYPE_OTHER),
                new ImportRecord(991_012, "", TYPE_SENTENCE),
                new ImportRecord(991_013, null, TYPE_SENTENCE),
                new ImportRecord(991_014, "x".repeat(500), TYPE_SENTENCE));

        var results = service.importBatch(records);

        assertEquals(List.of("Kept record"), results);
        assertEquals(1, service.getProcessedCount());
        assertEquals("Kept record", service.getLastProcessed());
    }

    @Test
    void importBatch_whitespaceOnlyTypeTwoRecord_isAcceptedReturnsEmptyAndCounts() {
        var results = service.importBatch(List.of(new ImportRecord(991_020, "   ", TYPE_SENTENCE)));

        assertEquals(List.of(""), results);
        assertEquals(1, service.getProcessedCount());
    }

    @Test
    void importBatch_emptyList_returnsEmptyListAndZeroCount() {
        var results = service.importBatch(List.of());

        assertTrue(results.isEmpty());
        assertEquals(0, service.getProcessedCount());
        assertEquals("", service.getLastProcessed());
    }

    @Test
    void importBatch_secondBatch_countIsPerBatchButLastProcessedCarriesOver() {
        service.importBatch(List.of(
                new ImportRecord(991_030, "First One", TYPE_SENTENCE),
                new ImportRecord(991_031, "First Two", TYPE_SENTENCE)));

        var results = service.importBatch(List.of(new ImportRecord(991_032, "rejected", TYPE_OTHER)));

        assertTrue(results.isEmpty());
        assertEquals(0, service.getProcessedCount());
        assertEquals("First two", service.getLastProcessed());
    }

    @Test
    void importBatch_processorNotInitializedBeforehand_initializesItSoRecordsAreAccepted() {
        var results = service.importBatch(List.of(new ImportRecord(991_040, "Ready Now", TYPE_SENTENCE)));

        assertEquals(List.of("Ready now"), results);
    }

    @Test
    void importBatch_nullList_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> service.importBatch(null));
    }

    @Test
    void importBatch_nullRecordInList_throwsNullPointerException() {
        var records = Arrays.asList(new ImportRecord(991_050, "Fine", TYPE_SENTENCE), null);

        assertThrows(NullPointerException.class, () -> service.importBatch(records));
    }

    @Test
    void getProcessedCount_beforeAnyBatch_returnsZero() {
        assertEquals(0, service.getProcessedCount());
        assertEquals("", service.getLastProcessed());
    }
}
