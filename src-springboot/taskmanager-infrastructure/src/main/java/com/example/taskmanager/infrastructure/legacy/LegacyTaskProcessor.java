package com.example.taskmanager.infrastructure.legacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Legacy task processing utility - originally written for v1 batch import pipeline.
// Kept for compatibility with existing integrations. Do not modify without testing.
@Component
public class LegacyTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(LegacyTaskProcessor.class);

    private static final int TYPE_FORMAT = 1;
    private static final int TYPE_SENTENCE = 2;
    private static final int MAX_RESULT_LENGTH = 50;
    private static final int MAX_INPUT_LENGTH = 500;
    private static final long WRITE_DELAY_MS = 100;

    private final TaskOutputWriter outputWriter;
    private boolean ready = false;
    private String lastResult = "";
    private int processedCount = 0;

    /**
     * Creates a processor that is not ready (so {@link #canProcess} returns {@code false} until {@link #init} is called),
     * with a processed count of 0 and an empty last result.
     *
     * @param outputWriter where type 1 results are written when they are formatted for export
     */
    public LegacyTaskProcessor(TaskOutputWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    /**
     * Starts a new processing run: sets whether {@link #canProcess} accepts input and resets the processed count to 0.
     * The last result is kept from the previous run. {@link #processTask} works the same whether or not the processor is ready.
     *
     * @param ready {@code true} to let {@link #canProcess} accept valid input; {@code false} to make it reject everything
     */
    public void init(boolean ready) {
        this.ready = ready;
        processedCount = 0;
    }

    /**
     * Transforms the task text according to its type, counts the call, and records the result as the last result.
     * It does not apply the {@link #canProcess} rules: it runs before {@link #init}, when not ready, for any type and any length.
     *
     * <ul>
     *   <li><b>Type 1 with {@code formatForExport}:</b> each space becomes {@code _} and the case of every other character
     *       is swapped one {@code char} at a time, so characters with no single-character counterpart (such as {@code ß})
     *       and characters outside the Basic Multilingual Plane (such as emoji) are unchanged. Tabs and newlines are kept.
     *       The result is then cut to its first 50 characters. The call blocks for at least 100 ms, then writes the result
     *       for {@code id} through the output writer. A failed write is logged and the result is still returned and counted;
     *       this includes a result whose truncation split a surrogate pair. If the thread is interrupted during the wait,
     *       the interruption is logged, the interrupt flag is left cleared, and the write still happens.</li>
     *   <li><b>Type 1 without {@code formatForExport}:</b> the whole text is uppercased using the default locale
     *       (so in Turkish {@code i} becomes {@code İ}, and {@code ß} becomes {@code SS}). It is not truncated and nothing is written.</li>
     *   <li><b>Type 2:</b> the text is split on single spaces; the first word is kept as it is and every later word is
     *       lowercased using the default locale, then the words are rejoined with single spaces. Leading and repeated
     *       spaces are kept, trailing spaces are dropped, text made only of spaces becomes {@code ""}, and tabs do not
     *       separate words. {@code formatForExport} is ignored and nothing is written.</li>
     *   <li><b>Any other type:</b> the text is returned unchanged and nothing is written.</li>
     * </ul>
     *
     * Null or empty text returns {@code ""} without counting the call, changing the last result, or writing anything.
     * Text made only of whitespace is treated as content.
     *
     * @param id identifies the task; used only to address the written output for type 1 with {@code formatForExport}
     * @param data the task text to transform; may be null or empty
     * @param type 1 to format, 2 to convert to sentence case; any other value passes the text through
     * @param formatForExport for type 1, {@code true} to case-swap, truncate and write the result, {@code false} to only
     *        uppercase it; ignored for other types
     * @return the transformed text, or {@code ""} if {@code data} is null or empty; never null
     */
    public String processTask(int id, String data, int type, boolean formatForExport) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        var result = transform(id, data, type, formatForExport);
        processedCount++;
        lastResult = result;
        return result;
    }

    /**
     * Reports whether a record should be passed to {@link #processTask}. Has no side effects.
     * Returns {@code true} only when all of these hold:
     * <ul>
     *   <li>the most recent {@link #init} call passed {@code true} (a new processor is not ready);</li>
     *   <li>{@code data} is not null or empty (text made only of whitespace is accepted);</li>
     *   <li>{@code type} is 1 or 2;</li>
     *   <li>{@code data} is shorter than 500 characters (499 is accepted, 500 is rejected).</li>
     * </ul>
     *
     * @param data the task text to check; may be null
     * @param type the transform type to check
     * @return {@code true} if the record meets every rule above, otherwise {@code false}
     */
    public boolean canProcess(String data, int type) {
        if (!ready) {
            return false;
        }
        if (data == null || data.isEmpty()) {
            return false;
        }
        if (type != TYPE_FORMAT && type != TYPE_SENTENCE) {
            return false;
        }
        return data.length() < MAX_INPUT_LENGTH;
    }

    /**
     * Returns the result of the most recent counted {@link #processTask} call. Calls with null or empty text don't change it,
     * and {@link #init} doesn't clear it, so it can come from an earlier run.
     *
     * @return the last result, or {@code ""} if no call has been counted since the processor was created; never null
     */
    public String getLastResult() { return lastResult; }

    /**
     * Returns how many {@link #processTask} calls have been counted since the processor was created or since the last
     * {@link #init}. Calls with null or empty text are not counted; every other call is, whatever its type.
     *
     * @return the number of counted calls in the current run; 0 or more
     */
    public int getProcessedCount() { return processedCount; }

    private String transform(int id, String data, int type, boolean formatForExport) {
        return switch (type) {
            case TYPE_FORMAT -> formatForExport ? formatAndExport(id, data) : data.toUpperCase();
            case TYPE_SENTENCE -> keepFirstWordAndLowercaseTheRest(data);
            default -> data;
        };
    }

    private String formatAndExport(int id, String data) {
        var formatted = truncateToMaximumLength(swapCaseAndReplaceSpaces(data));
        waitBeforeWrite(id);
        writeToFile(id, formatted);
        return formatted;
    }

    private String swapCaseAndReplaceSpaces(String data) {
        var result = new StringBuilder(data.length());
        for (int index = 0; index < data.length(); index++) {
            result.append(swapCaseOrReplaceSpace(data.charAt(index)));
        }
        return result.toString();
    }

    private char swapCaseOrReplaceSpace(char character) {
        if (character == ' ') {
            return '_';
        }
        if (Character.isUpperCase(character)) {
            return Character.toLowerCase(character);
        }
        return Character.toUpperCase(character);
    }

    private String truncateToMaximumLength(String value) {
        if (value.length() <= MAX_RESULT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_RESULT_LENGTH);
    }

    private void waitBeforeWrite(int id) {
        try {
            Thread.sleep(WRITE_DELAY_MS);
        } catch (InterruptedException exception) {
            // The interrupt flag is deliberately not restored: a batch caller left interrupted
            // would skip the delay for every later task (see docs/plans/legacy-refactor-plan.md).
            log.warn("Interrupted while waiting to write output for task {}", id, exception);
        }
    }

    private void writeToFile(int id, String content) {
        try {
            outputWriter.write(id, content);
        } catch (IOException exception) {
            log.warn("Failed to write output for task {}", id, exception);
        }
    }

    private String keepFirstWordAndLowercaseTheRest(String data) {
        var words = data.split(" ");
        if (words.length == 0) {
            return "";
        }

        var result = new StringBuilder(words[0]);
        for (int index = 1; index < words.length; index++) {
            result.append(' ').append(words[index].toLowerCase());
        }
        return result.toString();
    }
}
