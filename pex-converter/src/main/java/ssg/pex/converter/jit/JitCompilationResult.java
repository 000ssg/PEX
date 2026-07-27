package ssg.pex.converter.jit;

public record JitCompilationResult(Class<?> compiledClass, JitExecutable instance, long compilationTimeNanos) {
}
