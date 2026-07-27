package ssg.pex.result;

import ssg.pex.ast.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public sealed interface Result<T> permits Success, Failure {

    boolean isSuccess();
    boolean isFailure();
    T value();
    PexError error();

    <U> Result<U> map(Function<T, U> fn);
    <U> Result<U> flatMap(Function<T, Result<U>> fn);
    Result<T> mapError(Function<PexError, PexError> fn);
    T orElse(T defaultValue);
    T orElseGet(Supplier<T> supplier);
    <X extends Throwable> T orElseThrow(Function<PexError, X> exceptionMapper) throws X;
    Result<T> peek(Consumer<T> onSuccess);
    Result<T> peekError(Consumer<PexError> onFailure);
    Result<T> recover(Function<PexError, T> recovery);
    Result<T> recoverWith(Function<PexError, Result<T>> recovery);
    <U> U fold(Function<T, U> onSuccess, Function<PexError, U> onFailure);
    Optional<T> toOptional();
    Stream<T> stream();

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(PexError error) {
        return new Failure<>(error);
    }

    static <T> Result<T> failure(String code, String message) {
        return new Failure<>(new PexError(code, message));
    }

    static <T> Result<T> failure(String code, String message, SourceLocation location) {
        return new Failure<>(new PexError(code, message, location));
    }

    static <T> Result<T> failure(String code, String message, Throwable cause) {
        return new Failure<>(new PexError(code, message, cause));
    }

    static <T> Result<T> of(Callable<T> supplier) {
        try {
            return success(supplier.call());
        } catch (Exception e) {
            return failure(new PexError("EXCEPTION", e.getMessage(), e));
        }
    }

    static <T> Result<T> ofNullable(T value, Supplier<PexError> errorIfNull) {
        return value != null ? success(value) : failure(errorIfNull.get());
    }

    static <A, B> Result<Pair<A, B>> zip(Result<A> a, Result<B> b) {
        if (a.isFailure()) return failure(a.error());
        if (b.isFailure()) return failure(b.error());
        return success(new Pair<>(a.value(), b.value()));
    }

    static <T> Result<List<T>> sequence(List<Result<T>> results) {
        var values = new ArrayList<T>(results.size());
        for (var r : results) {
            if (r.isFailure()) return failure(r.error());
            values.add(r.value());
        }
        return success(List.copyOf(values));
    }

    static <T> BatchResult<T> collect(List<Result<T>> results) {
        var successes = new ArrayList<T>();
        var errors = new ArrayList<PexError>();
        for (var r : results) {
            if (r.isSuccess()) successes.add(r.value());
            else errors.add(r.error());
        }
        return new BatchResult<>(List.copyOf(successes), List.copyOf(errors), results.size());
    }

    record Pair<A, B>(A first, B second) {}
}
