package ssg.pex.result;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public record Success<T>(T value) implements Result<T> {

    @Override public boolean isSuccess() { return true; }
    @Override public boolean isFailure() { return false; }
    @Override public PexError error() { throw new NoSuchElementException("Success has no error"); }

    @Override public <U> Result<U> map(Function<T, U> fn) { return new Success<>(fn.apply(value)); }
    @Override public <U> Result<U> flatMap(Function<T, Result<U>> fn) { return fn.apply(value); }
    @Override public Result<T> mapError(Function<PexError, PexError> fn) { return this; }

    @Override public T orElse(T defaultValue) { return value; }
    @Override public T orElseGet(Supplier<T> supplier) { return value; }
    @Override public <X extends Throwable> T orElseThrow(Function<PexError, X> exceptionMapper) { return value; }

    @Override public Result<T> peek(Consumer<T> onSuccess) { onSuccess.accept(value); return this; }
    @Override public Result<T> peekError(Consumer<PexError> onFailure) { return this; }

    @Override public Result<T> recover(Function<PexError, T> recovery) { return this; }
    @Override public Result<T> recoverWith(Function<PexError, Result<T>> recovery) { return this; }

    @Override public <U> U fold(Function<T, U> onSuccess, Function<PexError, U> onFailure) {
        return onSuccess.apply(value);
    }

    @Override public Optional<T> toOptional() { return Optional.ofNullable(value); }
    @Override public Stream<T> stream() { return value != null ? Stream.of(value) : Stream.empty(); }
}
