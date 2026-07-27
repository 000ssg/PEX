package ssg.pex.result;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public record Failure<T>(PexError error) implements Result<T> {

    @Override public boolean isSuccess() { return false; }
    @Override public boolean isFailure() { return true; }
    @Override public T value() { throw new NoSuchElementException("Failure: " + error); }

    @Override @SuppressWarnings("unchecked")
    public <U> Result<U> map(Function<T, U> fn) { return (Result<U>) this; }
    @Override @SuppressWarnings("unchecked")
    public <U> Result<U> flatMap(Function<T, Result<U>> fn) { return (Result<U>) this; }
    @Override public Result<T> mapError(Function<PexError, PexError> fn) { return new Failure<>(fn.apply(error)); }

    @Override public T orElse(T defaultValue) { return defaultValue; }
    @Override public T orElseGet(Supplier<T> supplier) { return supplier.get(); }
    @Override public <X extends Throwable> T orElseThrow(Function<PexError, X> exceptionMapper) throws X {
        throw exceptionMapper.apply(error);
    }

    @Override public Result<T> peek(Consumer<T> onSuccess) { return this; }
    @Override public Result<T> peekError(Consumer<PexError> onFailure) { onFailure.accept(error); return this; }

    @Override public Result<T> recover(Function<PexError, T> recovery) { return new Success<>(recovery.apply(error)); }
    @Override public Result<T> recoverWith(Function<PexError, Result<T>> recovery) { return recovery.apply(error); }

    @Override public <U> U fold(Function<T, U> onSuccess, Function<PexError, U> onFailure) {
        return onFailure.apply(error);
    }

    @Override public Optional<T> toOptional() { return Optional.empty(); }
    @Override public Stream<T> stream() { return Stream.empty(); }
}
