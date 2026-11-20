package com.example.taskmanager.infrastructure.legacy;

import java.util.Arrays;

/**
 * How LegacyTaskProcessor transforms task text. Codes are the integers used by the v1 batch import format.
 */
public enum TransformType {
    FORMAT(1),
    SENTENCE(2),
    PASS_THROUGH(-1);

    private final int code;

    TransformType(int code) {
        this.code = code;
    }

    /** Maps a v1 import code to its type. Any unknown code passes the text through unchanged. */
    public static TransformType fromCode(int code) {
        return Arrays.stream(values())
                .filter(type -> type != PASS_THROUGH && type.code == code)
                .findFirst()
                .orElse(PASS_THROUGH);
    }
}
