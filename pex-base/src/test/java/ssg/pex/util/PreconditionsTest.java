package ssg.pex.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("Preconditions utility")
class PreconditionsTest {

    @Nested
    @DisplayName("requireNonNull")
    class RequireNonNullTests {

        @Test
        @DisplayName("returns value when non-null")
        void testReturnsValueWhenNonNull() {
            String value = "hello";
            String result = Preconditions.requireNonNull(value, "value");
            assertThat(result).isSameAs(value);
        }

        @Test
        @DisplayName("returns non-string value unchanged")
        void testReturnsNonStringValue() {
            Integer num = 42;
            Integer result = Preconditions.requireNonNull(num, "num");
            assertThat(result).isEqualTo(42);
        }

        @Test
        @DisplayName("throws when null with descriptive message")
        void testThrowsWhenNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Preconditions.requireNonNull(null, "myParam"))
                    .withMessage("myParam must not be null");
        }

        @Test
        @DisplayName("includes parameter name in error message")
        void testErrorMessageContainsName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Preconditions.requireNonNull(null, "grammarFile"))
                    .withMessageContaining("grammarFile");
        }
    }

    @Nested
    @DisplayName("requireTrue")
    class RequireTrueTests {

        @Test
        @DisplayName("does not throw when condition is true")
        void testDoesNotThrowWhenTrue() {
            // Should not throw
            Preconditions.requireTrue(true, "condition must be true");
            Preconditions.requireTrue(1 + 1 == 2, "math is broken");
        }

        @Test
        @DisplayName("throws when condition is false")
        void testThrowsWhenFalse() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Preconditions.requireTrue(false, "value must be positive"))
                    .withMessage("value must be positive");
        }

        @Test
        @DisplayName("message is preserved exactly")
        void testMessagePreserved() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Preconditions.requireTrue(1 > 2, "expected 1 > 2"))
                    .withMessage("expected 1 > 2");
        }
    }

    @Nested
    @DisplayName("requireNotEmpty")
    class RequireNotEmptyTests {

        @Test
        @DisplayName("does not throw for non-empty string")
        void testDoesNotThrowForNonEmpty() {
            // Should not throw
            Preconditions.requireNotEmpty("hello", "param");
            Preconditions.requireNotEmpty("  ", "param"); // whitespace-only is not empty by this check
        }

        @Test
        @DisplayName("throws for null string")
        void testThrowsForNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Preconditions.requireNotEmpty(null, "ruleName"))
                    .withMessage("ruleName must not be null or empty");
        }

        @Test
        @DisplayName("throws for empty string")
        void testThrowsForEmpty() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Preconditions.requireNotEmpty("", "ruleName"))
                    .withMessage("ruleName must not be null or empty");
        }

        @Test
        @DisplayName("message includes parameter name")
        void testErrorMessageContainsName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Preconditions.requireNotEmpty("", "grammarName"))
                    .withMessageContaining("grammarName");
        }
    }
}
