package com.example.taskmanager.domain.tasks;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Task priority. The level orders priorities from least (LOW) to most (CRITICAL) urgent.
 */
public enum Priority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private static final String VALID_VALUES = Arrays.stream(values())
        .map(Priority::name)
        .collect(Collectors.joining(", "));

    private final int level;

    Priority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Parses a priority name, ignoring case and surrounding whitespace.
     *
     * @throws IllegalArgumentException if value is null, blank, or not a priority name
     */
    public static Priority fromString(String value) {
        if (value == null || value.isBlank()) {
            throw invalid(value);
        }
        var normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(priority -> priority.name().equals(normalized))
            .findFirst()
            .orElseThrow(() -> invalid(value));
    }

    private static IllegalArgumentException invalid(String value) {
        return new IllegalArgumentException(
            String.format("Invalid priority '%s'. Valid values: %s", value, VALID_VALUES)
        );
    }
}
