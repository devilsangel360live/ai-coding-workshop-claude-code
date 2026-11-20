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

    private final TaskOutputWriter outputWriter;
    private boolean ready = false;
    private String lastResult = "";
    private int processedCount = 0;

    public LegacyTaskProcessor(TaskOutputWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    public void init(boolean ready) {
        this.ready = ready;
        processedCount = 0;
    }

    public String processTask(int id, String data, TransformType type, boolean formatForExport) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        var result = transform(id, data, type, formatForExport);
        processedCount++;
        lastResult = result;
        return result;
    }

    public boolean canProcess(String data, TransformType type) {
        if (!ready) {
            return false;
        }
        if (data == null || data.isEmpty()) {
            return false;
        }
        if (type == TransformType.PASS_THROUGH) {
            return false;
        }
        return data.length() < 500;
    }

    public String getLastResult() { return lastResult; }

    public int getProcessedCount() { return processedCount; }

    private String transform(int id, String data, TransformType type, boolean formatForExport) {
        if (type == TransformType.FORMAT && formatForExport) {
            return formatAndExport(id, data);
        }
        if (type == TransformType.FORMAT) {
            return data.toUpperCase();
        }
        if (type == TransformType.SENTENCE) {
            return keepFirstWordAndLowercaseTheRest(data);
        }
        return data;
    }

    private String formatAndExport(int id, String data) {
        var formatted = truncateToMaximumLength(swapCaseAndReplaceSpaces(data));
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
        if (value.length() <= 50) {
            return value;
        }
        return value.substring(0, 50);
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
