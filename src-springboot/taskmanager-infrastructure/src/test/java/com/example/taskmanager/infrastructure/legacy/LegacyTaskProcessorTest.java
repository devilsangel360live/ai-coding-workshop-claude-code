package com.example.taskmanager.infrastructure.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for LegacyTaskProcessor.
 * They pin the CURRENT behavior, bugs included, so the Lab 5 refactor can prove it changed nothing.
 * A failing test here means behavior changed: revert the refactor step, don't edit the test.
 * See docs/plans/legacy-refactor-plan.md for the behavior table these tests cover.
 */
@DisplayName("LegacyTaskProcessor characterization tests")
class LegacyTaskProcessorTest {

    private static final int TYPE_FORMAT = 1;
    private static final int TYPE_SENTENCE = 2;
    private static final int TYPE_OTHER = 3;

    private LegacyTaskProcessor processor;
    private final TaskOutputFiles outputFiles = new TaskOutputFiles();

    @BeforeEach
    void setUp() {
        processor = new LegacyTaskProcessor(new TaskResultFileWriter());
    }

    @AfterEach
    void deleteCreatedFiles() throws IOException {
        outputFiles.deleteAll();
    }

    private static long millisecondsSince(long startNanoseconds) {
        return (System.nanoTime() - startNanoseconds) / 1_000_000;
    }

    @Nested
    @DisplayName("initial state")
    class InitialState {

        @Test
        void getLastResult_newInstance_returnsEmptyString() {
            assertEquals("", processor.getLastResult());
        }

        @Test
        void getProcessedCount_newInstance_returnsZero() {
            assertEquals(0, processor.getProcessedCount());
        }
    }

    @Nested
    @DisplayName("processTask with null or empty input")
    class NullOrEmptyInput {

        @ParameterizedTest
        @NullAndEmptySource
        void processTask_nullOrEmptyData_returnsEmptyStringWithoutCountingOrUpdatingLastResult(String data) {
            processor.processTask(990_100, "Previous", TYPE_OTHER, false);

            var result = processor.processTask(990_101, data, TYPE_FORMAT, true);

            assertEquals("", result);
            assertEquals(1, processor.getProcessedCount());
            assertEquals("Previous", processor.getLastResult());
        }

        @ParameterizedTest
        @NullAndEmptySource
        void processTask_nullOrEmptyDataWithFlag_writesNoFile(String data) throws IOException {
            var file = outputFiles.track(990_102);

            processor.processTask(990_102, data, TYPE_FORMAT, true);

            assertFalse(Files.exists(file));
        }
    }

    @Nested
    @DisplayName("processTask type 1 with formatForExport=true")
    class FormatForExport {

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "Hello World      | hELLO_wORLD",
                "abc XYZ 123      | ABC_xyz_123",
                "'   '            | ___",
                "ǅ                | Ǆ",
                "ß                | ß",
                "İ                | i",
                "𐐀                | 𐐀",
                "😀               | 😀"
        })
        void processTask_typeOneWithFlag_swapsCasePerCharAndReplacesSpaces(String data, String expected) throws IOException {
            outputFiles.track(990_200);

            var result = processor.processTask(990_200, data, TYPE_FORMAT, true);

            assertEquals(expected, result);
        }

        @Test
        void processTask_typeOneWithFlagAndTabOrNewline_swapsCaseAndKeepsThem() throws IOException {
            outputFiles.track(990_201);

            var result = processor.processTask(990_201, "a\tb\nc", TYPE_FORMAT, true);

            assertEquals("A\tB\nC", result);
        }

        @Test
        void processTask_typeOneWithFlagAndSixtyChars_truncatesToFiftyAfterTransform() throws IOException {
            outputFiles.track(990_202);

            var result = processor.processTask(990_202, "a".repeat(60), TYPE_FORMAT, true);

            assertEquals("A".repeat(50), result);
        }

        @Test
        void processTask_typeOneWithFlagAndExactlyFiftyChars_doesNotTruncate() throws IOException {
            outputFiles.track(990_203);

            var result = processor.processTask(990_203, "b".repeat(50), TYPE_FORMAT, true);

            assertEquals("B".repeat(50), result);
        }

        @Test
        void processTask_typeOneWithFlag_writesReturnedValueToFileInWorkingDirectory() throws IOException {
            var file = outputFiles.track(990_204);

            var result = processor.processTask(990_204, "Mixed Case " + "x".repeat(50), TYPE_FORMAT, true);

            assertTrue(Files.exists(file));
            assertEquals(result, Files.readString(file));
        }

        @Test
        void processTask_typeOneWithFlagAndExistingFile_overwritesIt() throws IOException {
            var file = outputFiles.track(990_205);
            Files.writeString(file, "old content that is much longer than the new one");

            processor.processTask(990_205, "new", TYPE_FORMAT, true);

            assertEquals("NEW", Files.readString(file));
        }

        @Test
        void processTask_typeOneWithFlag_waitsAtLeastOneHundredMilliseconds() throws IOException {
            outputFiles.track(990_206);
            var start = System.nanoTime();

            processor.processTask(990_206, "slow", TYPE_FORMAT, true);

            var elapsed = millisecondsSince(start);
            assertTrue(elapsed >= 100, "expected >= 100 ms but was " + elapsed);
        }

        @Test
        void processTask_typeOneWithFlagWhenWriteFails_stillReturnsAndCountsResult() throws IOException {
            var blockingDirectory = outputFiles.track(990_207);
            Files.createDirectory(blockingDirectory);

            var result = processor.processTask(990_207, "Write Fails", TYPE_FORMAT, true);

            assertEquals("wRITE_fAILS", result);
            assertEquals(1, processor.getProcessedCount());
            assertEquals("wRITE_fAILS", processor.getLastResult());
            assertTrue(Files.isDirectory(blockingDirectory));
        }

        @Test
        void processTask_typeOneWithFlagWhenTruncationSplitsSurrogatePair_writesNoFileButReturnsAndCounts() throws IOException {
            var file = outputFiles.track(990_208);

            var result = processor.processTask(990_208, "a".repeat(49) + "😀", TYPE_FORMAT, true);

            assertEquals("A".repeat(49) + "😀".charAt(0), result);
            assertEquals(1, processor.getProcessedCount());
            assertFalse(Files.exists(file));
        }

        @Test
        void processTask_typeOneWithFlagOnInterruptedThread_clearsInterruptFlagAndStillWritesFile() throws IOException {
            var file = outputFiles.track(990_209);
            Thread.currentThread().interrupt();

            try {
                processor.processTask(990_209, "interrupted", TYPE_FORMAT, true);

                assertFalse(Thread.currentThread().isInterrupted());
                assertEquals("INTERRUPTED", Files.readString(file));
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Nested
    @DisplayName("processTask type 1 with formatForExport=false")
    class UppercaseOnly {

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "Hello World | HELLO WORLD",
                "ß           | SS",
                "a\tb        | A\tB"
        })
        void processTask_typeOneWithoutFlag_uppercasesWholeString(String data, String expected) {
            var result = processor.processTask(990_300, data, TYPE_FORMAT, false);

            assertEquals(expected, result);
        }

        @Test
        void processTask_typeOneWithoutFlagAndSixtyChars_doesNotTruncate() {
            var result = processor.processTask(990_301, "a".repeat(60), TYPE_FORMAT, false);

            assertEquals("A".repeat(60), result);
        }

        @Test
        void processTask_typeOneWithoutFlag_writesNoFile() throws IOException {
            var file = outputFiles.track(990_302);

            processor.processTask(990_302, "fast", TYPE_FORMAT, false);

            assertFalse(Files.exists(file));
        }
    }

    @Nested
    @DisplayName("processTask type 2 (sentence)")
    class Sentence {

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "Hello World        | Hello world",
                "HELLO WORLD AGAIN  | HELLO world again",
                "hello              | hello",
                "' Hello World'     | ' hello world'",
                "'a  B'             | 'a  b'",
                "'Hello World '     | Hello world",
                "A\tB               | A\tB"
        })
        void processTask_typeTwo_keepsFirstWordAndLowercasesTheRest(String data, String expected) {
            var result = processor.processTask(990_400, data, TYPE_SENTENCE, false);

            assertEquals(expected, result);
        }

        @Test
        void processTask_typeTwoWithOnlySpaces_returnsEmptyStringButStillCounts() {
            var result = processor.processTask(990_401, "   ", TYPE_SENTENCE, false);

            assertEquals("", result);
            assertEquals(1, processor.getProcessedCount());
            assertEquals("", processor.getLastResult());
        }

        @Test
        void processTask_typeTwoWithFlag_ignoresFlagAndWritesNoFile() throws IOException {
            var file = outputFiles.track(990_402);

            var result = processor.processTask(990_402, "Hello World", TYPE_SENTENCE, true);

            assertEquals("Hello world", result);
            assertFalse(Files.exists(file));
        }
    }

    @Nested
    @DisplayName("processTask other types")
    class OtherTypes {

        @ParameterizedTest
        @ValueSource(ints = {0, 3, -1, 99})
        void processTask_unknownType_returnsInputUnchangedAndCounts(int type) {
            var result = processor.processTask(990_500, "Keep Me As Is", type, true);

            assertEquals("Keep Me As Is", result);
            assertEquals(1, processor.getProcessedCount());
            assertEquals("Keep Me As Is", processor.getLastResult());
        }
    }

    @Nested
    @DisplayName("processTask ignores the validation rules in canProcess")
    class ProcessTaskIgnoresValidation {

        @Test
        void processTask_beforeInit_stillProcesses() {
            var result = processor.processTask(990_600, "abc", TYPE_FORMAT, false);

            assertEquals("ABC", result);
            assertEquals(1, processor.getProcessedCount());
        }

        @Test
        void processTask_afterInitFalse_stillProcesses() {
            processor.init(false);

            var result = processor.processTask(990_601, "abc", TYPE_FORMAT, false);

            assertEquals("ABC", result);
        }

        @Test
        void processTask_inputOfSixHundredChars_stillProcesses() {
            var data = "z".repeat(600);

            var result = processor.processTask(990_602, data, TYPE_OTHER, false);

            assertEquals(data, result);
        }
    }

    @Nested
    @DisplayName("count and last result")
    class CountAndLast {

        @Test
        void processTask_severalCalls_countsEachAndKeepsLastResult() {
            processor.processTask(990_700, "one", TYPE_FORMAT, false);
            processor.processTask(990_701, "Two Words", TYPE_SENTENCE, false);
            processor.processTask(990_702, "three", TYPE_OTHER, false);

            assertEquals(3, processor.getProcessedCount());
            assertEquals("three", processor.getLastResult());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void init_afterProcessing_resetsCountButNotLastResult(boolean ready) {
            processor.processTask(990_703, "abc", TYPE_FORMAT, false);

            processor.init(ready);

            assertEquals(0, processor.getProcessedCount());
            assertEquals("ABC", processor.getLastResult());
        }
    }

    @Nested
    @DisplayName("canProcess")
    class CanProcess {

        @ParameterizedTest
        @ValueSource(ints = {TYPE_FORMAT, TYPE_SENTENCE})
        void canProcess_beforeInit_returnsFalse(int type) {
            var result = processor.canProcess("valid", type);

            assertFalse(result);
        }

        @Test
        void canProcess_afterInitFalse_returnsFalse() {
            processor.init(false);

            var result = processor.canProcess("valid", TYPE_FORMAT);

            assertFalse(result);
        }

        @ParameterizedTest
        @ValueSource(ints = {TYPE_FORMAT, TYPE_SENTENCE})
        void canProcess_readyWithSupportedType_returnsTrue(int type) {
            processor.init(true);

            var result = processor.canProcess("valid", type);

            assertTrue(result);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 3, -1})
        void canProcess_readyWithUnsupportedType_returnsFalse(int type) {
            processor.init(true);

            var result = processor.canProcess("valid", type);

            assertFalse(result);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void canProcess_readyWithNullOrEmptyData_returnsFalse(String data) {
            processor.init(true);

            var result = processor.canProcess(data, TYPE_FORMAT);

            assertFalse(result);
        }

        @Test
        void canProcess_readyWithWhitespaceOnlyData_returnsTrue() {
            processor.init(true);

            var result = processor.canProcess("   ", TYPE_FORMAT);

            assertTrue(result);
        }

        @ParameterizedTest
        @CsvSource({"499, true", "500, false", "501, false"})
        void canProcess_readyAtLengthBoundary_acceptsOnlyUnderFiveHundred(int length, boolean expected) {
            processor.init(true);

            var result = processor.canProcess("x".repeat(length), TYPE_SENTENCE);

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("default-locale dependence (Turkish)")
    class TurkishLocale {

        private Locale originalLocale;

        @BeforeEach
        void useTurkishLocale() {
            originalLocale = Locale.getDefault();
            Locale.setDefault(Locale.forLanguageTag("tr"));
        }

        @AfterEach
        void restoreLocale() {
            Locale.setDefault(originalLocale);
        }

        @Test
        void processTask_typeOneWithoutFlagInTurkish_uppercasesToDottedI() {
            var result = processor.processTask(990_800, "i", TYPE_FORMAT, false);

            assertEquals("İ", result);
        }

        @Test
        void processTask_typeTwoInTurkish_lowercasesToDotlessI() {
            var result = processor.processTask(990_801, "Hello I", TYPE_SENTENCE, false);

            assertEquals("Hello ı", result);
        }

        @Test
        void processTask_typeOneWithFlagInTurkish_swapIsLocaleIndependent() throws IOException {
            outputFiles.track(990_802);

            var result = processor.processTask(990_802, "i", TYPE_FORMAT, true);

            assertEquals("I", result);
        }
    }
}
