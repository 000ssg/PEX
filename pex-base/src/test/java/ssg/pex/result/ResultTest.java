package ssg.pex.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.SourceLocation;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultTest {

    @Nested
    class SuccessTests {

        private final Result<String> success = Result.success("hello");

        @Test
        @DisplayName("isSuccess returns true")
        void isSuccess() {
            assertThat(success.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("isFailure returns false")
        void isFailure() {
            assertThat(success.isFailure()).isFalse();
        }

        @Test
        @DisplayName("value returns the wrapped value")
        void value() {
            assertThat(success.value()).isEqualTo("hello");
        }

        @Test
        @DisplayName("error throws NoSuchElementException")
        void errorThrows() {
            assertThatThrownBy(success::error)
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("map transforms the value")
        void map() {
            Result<Integer> mapped = success.map(String::length);
            assertThat(mapped.isSuccess()).isTrue();
            assertThat(mapped.value()).isEqualTo(5);
        }

        @Test
        @DisplayName("flatMap transforms with a Result-returning function")
        void flatMap() {
            Result<Integer> result = success.flatMap(s -> Result.success(s.length()));
            assertThat(result.value()).isEqualTo(5);
        }

        @Test
        @DisplayName("mapError is a no-op on success")
        void mapError() {
            Result<String> result = success.mapError(e -> new PexError("X", "mapped"));
            assertThat(result).isSameAs(success);
        }

        @Test
        @DisplayName("orElse returns the success value, not the default")
        void orElse() {
            assertThat(success.orElse("default")).isEqualTo("hello");
        }

        @Test
        @DisplayName("orElseGet returns the success value, supplier not called")
        void orElseGet() {
            assertThat(success.orElseGet(() -> "default")).isEqualTo("hello");
        }

        @Test
        @DisplayName("orElseThrow returns the success value without throwing")
        void orElseThrow() {
            assertThat(success.orElseThrow(e -> new RuntimeException())).isEqualTo("hello");
        }

        @Test
        @DisplayName("peek invokes the consumer with the value")
        void peek() {
            var ref = new AtomicReference<String>();
            success.peek(ref::set);
            assertThat(ref.get()).isEqualTo("hello");
        }

        @Test
        @DisplayName("peekError does not invoke the consumer")
        void peekError() {
            var ref = new AtomicReference<PexError>();
            success.peekError(ref::set);
            assertThat(ref.get()).isNull();
        }

        @Test
        @DisplayName("recover is a no-op on success")
        void recover() {
            Result<String> result = success.recover(e -> "recovered");
            assertThat(result).isSameAs(success);
        }

        @Test
        @DisplayName("recoverWith is a no-op on success")
        void recoverWith() {
            Result<String> result = success.recoverWith(e -> Result.success("recovered"));
            assertThat(result).isSameAs(success);
        }

        @Test
        @DisplayName("fold applies onSuccess function")
        void fold() {
            int len = success.fold(String::length, e -> -1);
            assertThat(len).isEqualTo(5);
        }

        @Test
        @DisplayName("toOptional returns non-empty Optional")
        void toOptional() {
            assertThat(success.toOptional()).hasValue("hello");
        }

        @Test
        @DisplayName("stream contains the value")
        void stream() {
            assertThat(success.stream()).containsExactly("hello");
        }
    }

    @Nested
    class FailureTests {

        private final PexError error = new PexError("ERR", "something went wrong");
        private final Result<String> failure = Result.failure(error);

        @Test
        @DisplayName("isSuccess returns false")
        void isSuccess() {
            assertThat(failure.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("isFailure returns true")
        void isFailure() {
            assertThat(failure.isFailure()).isTrue();
        }

        @Test
        @DisplayName("value throws NoSuchElementException")
        void valueThrows() {
            assertThatThrownBy(failure::value)
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("error returns the PexError")
        void error() {
            assertThat(failure.error()).isSameAs(error);
        }

        @Test
        @DisplayName("map propagates the failure without calling the function")
        void mapPropagates() {
            Result<Integer> mapped = failure.map(s -> {
                throw new AssertionError("should not be called");
            });
            assertThat(mapped.isFailure()).isTrue();
            assertThat(mapped.error()).isSameAs(error);
        }

        @Test
        @DisplayName("flatMap propagates the failure without calling the function")
        void flatMapPropagates() {
            Result<Integer> result = failure.flatMap(s -> {
                throw new AssertionError("should not be called");
            });
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error()).isSameAs(error);
        }

        @Test
        @DisplayName("mapError transforms the error")
        void mapError() {
            Result<String> mapped = failure.mapError(e -> new PexError("NEW", "new message"));
            assertThat(mapped.isFailure()).isTrue();
            assertThat(mapped.error().code()).isEqualTo("NEW");
        }

        @Test
        @DisplayName("orElse returns the default value")
        void orElse() {
            assertThat(failure.orElse("default")).isEqualTo("default");
        }

        @Test
        @DisplayName("orElseGet calls the supplier and returns its value")
        void orElseGet() {
            assertThat(failure.orElseGet(() -> "supplied")).isEqualTo("supplied");
        }

        @Test
        @DisplayName("orElseThrow throws the mapped exception")
        void orElseThrow() {
            assertThatThrownBy(() -> failure.orElseThrow(e -> new IllegalStateException(e.message())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("something went wrong");
        }

        @Test
        @DisplayName("peek does not invoke the consumer")
        void peek() {
            var ref = new AtomicReference<String>();
            failure.peek(ref::set);
            assertThat(ref.get()).isNull();
        }

        @Test
        @DisplayName("peekError invokes the consumer with the error")
        void peekError() {
            var ref = new AtomicReference<PexError>();
            failure.peekError(ref::set);
            assertThat(ref.get()).isSameAs(error);
        }

        @Test
        @DisplayName("recover applies recovery function and returns success")
        void recover() {
            Result<String> result = failure.recover(e -> "recovered");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo("recovered");
        }

        @Test
        @DisplayName("recoverWith applies recovery function returning a Result")
        void recoverWith() {
            Result<String> result = failure.recoverWith(e -> Result.success("recovered"));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo("recovered");
        }

        @Test
        @DisplayName("fold applies onFailure function")
        void fold() {
            String code = failure.fold(s -> "ok", e -> e.code());
            assertThat(code).isEqualTo("ERR");
        }

        @Test
        @DisplayName("toOptional returns empty")
        void toOptional() {
            assertThat(failure.toOptional()).isEmpty();
        }

        @Test
        @DisplayName("stream is empty")
        void stream() {
            assertThat(failure.stream()).isEmpty();
        }
    }

    @Nested
    class StaticFactories {

        @Test
        @DisplayName("success wraps a value")
        void success() {
            Result<Integer> r = Result.success(42);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isEqualTo(42);
        }

        @Test
        @DisplayName("failure(PexError) wraps an error")
        void failureWithPexError() {
            var err = new PexError("C", "msg");
            Result<String> r = Result.failure(err);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error()).isSameAs(err);
        }

        @Test
        @DisplayName("failure(code, message) creates a PexError")
        void failureWithCodeAndMessage() {
            Result<String> r = Result.failure("CODE", "detail");
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("CODE");
            assertThat(r.error().message()).isEqualTo("detail");
        }

        @Test
        @DisplayName("failure(code, message, location) preserves the location")
        void failureWithLocation() {
            var loc = SourceLocation.of(10, 5);
            Result<String> r = Result.failure("LOC", "msg", loc);
            assertThat(r.error().location()).isEqualTo(loc);
        }

        @Test
        @DisplayName("of(callable) returns success when callable succeeds")
        void ofSuccess() {
            Result<String> r = Result.of(() -> "ok");
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isEqualTo("ok");
        }

        @Test
        @DisplayName("of(callable) returns failure when callable throws")
        void ofException() {
            Result<String> r = Result.of(() -> { throw new RuntimeException("boom"); });
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("EXCEPTION");
            assertThat(r.error().message()).isEqualTo("boom");
        }

        @Test
        @DisplayName("ofNullable returns success for non-null value")
        void ofNullableNonNull() {
            Result<String> r = Result.ofNullable("val", () -> new PexError("NULL", "was null"));
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).isEqualTo("val");
        }

        @Test
        @DisplayName("ofNullable returns failure for null value")
        void ofNullableNull() {
            Result<String> r = Result.ofNullable(null, () -> new PexError("NULL", "was null"));
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("NULL");
        }
    }

    @Nested
    class Combining {

        @Test
        @DisplayName("zip returns pair when both succeed")
        void zipBothSuccess() {
            Result<Result.Pair<String, Integer>> r = Result.zip(Result.success("a"), Result.success(1));
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value().first()).isEqualTo("a");
            assertThat(r.value().second()).isEqualTo(1);
        }

        @Test
        @DisplayName("zip returns failure when first fails")
        void zipFirstFailure() {
            Result<Result.Pair<String, Integer>> r =
                    Result.zip(Result.failure("F1", "first"), Result.success(1));
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("F1");
        }

        @Test
        @DisplayName("zip returns failure when second fails")
        void zipSecondFailure() {
            Result<Result.Pair<String, Integer>> r =
                    Result.zip(Result.success("a"), Result.failure("F2", "second"));
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("F2");
        }

        @Test
        @DisplayName("sequence returns all values when all succeed")
        void sequenceAllSuccess() {
            var results = List.of(Result.success(1), Result.success(2), Result.success(3));
            Result<List<Integer>> r = Result.sequence(results);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.value()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("sequence returns failure on first failure encountered")
        void sequenceOneFailure() {
            var results = List.<Result<Integer>>of(
                    Result.success(1),
                    Result.failure("FAIL", "second failed"),
                    Result.success(3)
            );
            Result<List<Integer>> r = Result.sequence(results);
            assertThat(r.isFailure()).isTrue();
            assertThat(r.error().code()).isEqualTo("FAIL");
        }

        @Test
        @DisplayName("collect gathers successes and errors separately")
        void collectMixed() {
            var results = List.<Result<String>>of(
                    Result.success("a"),
                    Result.failure("E1", "err1"),
                    Result.success("b"),
                    Result.failure("E2", "err2")
            );
            BatchResult<String> batch = Result.collect(results);
            assertThat(batch.successes()).containsExactly("a", "b");
            assertThat(batch.errors()).hasSize(2);
            assertThat(batch.totalAttempted()).isEqualTo(4);
        }
    }

    @Nested
    class BatchResultTests {

        @Test
        @DisplayName("hasErrors returns true when errors exist")
        void hasErrors() {
            var batch = new BatchResult<>(List.of("a"), List.of(new PexError("E", "e")), 2);
            assertThat(batch.hasErrors()).isTrue();
        }

        @Test
        @DisplayName("isFullSuccess returns true when no errors")
        void isFullSuccess() {
            var batch = new BatchResult<>(List.of("a", "b"), List.of(), 2);
            assertThat(batch.isFullSuccess()).isTrue();
        }

        @Test
        @DisplayName("successCount and errorCount reflect list sizes")
        void counts() {
            var batch = new BatchResult<>(List.of("a"), List.of(new PexError("E", "e")), 2);
            assertThat(batch.successCount()).isEqualTo(1);
            assertThat(batch.errorCount()).isEqualTo(1);
        }
    }
}
