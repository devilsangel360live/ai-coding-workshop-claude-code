package com.example.taskmanager.domain.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Priority Value Object")
class PriorityTest {

    @Nested
    @DisplayName("Values and Levels")
    class ValuesAndLevels {

        @Test
        void values_declared_areLowMediumHighCriticalInOrder() {
            // Act
            var values = Priority.values();

            // Assert
            assertArrayEquals(
                new Priority[] {Priority.LOW, Priority.MEDIUM, Priority.HIGH, Priority.CRITICAL},
                values
            );
        }

        @ParameterizedTest
        @CsvSource({"LOW, 1", "MEDIUM, 2", "HIGH, 3", "CRITICAL, 4"})
        void getLevel_eachPriority_returnsOrderingLevel(Priority priority, int expectedLevel) {
            // Act
            var level = priority.getLevel();

            // Assert
            assertEquals(expectedLevel, level);
        }
    }

    @Nested
    @DisplayName("fromString Parsing")
    class FromStringParsing {

        @ParameterizedTest
        @ValueSource(strings = {"high", "HIGH", "High", "hIgH"})
        void fromString_anyCase_returnsHigh(String value) {
            // Act
            var priority = Priority.fromString(value);

            // Assert
            assertEquals(Priority.HIGH, priority);
        }

        @ParameterizedTest
        @CsvSource({"low, LOW", "medium, MEDIUM", "high, HIGH", "critical, CRITICAL"})
        void fromString_eachValidName_returnsMatchingPriority(String value, Priority expected) {
            // Act
            var priority = Priority.fromString(value);

            // Assert
            assertEquals(expected, priority);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "Urgent", "none", "1"})
        void fromString_invalidValue_throwsIllegalArgumentExceptionListingValidValues(String value) {
            // Act
            var exception = assertThrows(IllegalArgumentException.class, () -> Priority.fromString(value));

            // Assert
            assertTrue(
                exception.getMessage().contains("Valid values: LOW, MEDIUM, HIGH, CRITICAL"),
                () -> "Message should list valid values but was: " + exception.getMessage()
            );
        }
    }
}
