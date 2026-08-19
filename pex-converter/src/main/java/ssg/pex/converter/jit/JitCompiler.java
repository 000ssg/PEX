package ssg.pex.converter.jit;

import ssg.pex.result.Result;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JitCompiler {

    public Result<JitCompilationResult> compile(String javaSource, String className) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return Result.failure("JIT_NO_COMPILER",
                    "No Java compiler available. Ensure you are running on a JDK, not a JRE.");
        }

        long startNanos = System.nanoTime();

        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var classBytes = new ConcurrentHashMap<String, byte[]>();

        var sourceFile = new InMemorySourceFile(className, javaSource);
        var fileManager = new InMemoryFileManager(
                compiler.getStandardFileManager(diagnostics, null, null), classBytes);

        var task = compiler.getTask(
                new StringWriter(), fileManager, diagnostics,
                null, null, List.of(sourceFile));

        boolean success = task.call();
        long compilationTimeNanos = System.nanoTime() - startNanos;

        if (!success) {
            var sb = new StringBuilder("Compilation failed:\n");
            for (var d : diagnostics.getDiagnostics()) {
                sb.append(d.toString()).append('\n');
            }
            return Result.failure("JIT_COMPILATION_ERROR", sb.toString());
        }

        try {
            var classLoader = new JitClassLoader(getClass().getClassLoader());
            for (var entry : classBytes.entrySet()) {
                classLoader.addClass(entry.getKey(), entry.getValue());
            }

            Class<?> compiledClass = classLoader.loadClass(className);
            Object instance = compiledClass.getDeclaredConstructor().newInstance();

            if (!(instance instanceof JitExecutable executable)) {
                return Result.failure("JIT_NOT_EXECUTABLE",
                        "Compiled class does not implement JitExecutable");
            }

            return Result.success(new JitCompilationResult(
                    compiledClass, executable, compilationTimeNanos));
        } catch (Exception e) {
            return Result.failure("JIT_LOAD_ERROR", "Failed to load compiled class: " + e.getMessage(), e);
        }
    }

    private static class InMemorySourceFile extends SimpleJavaFileObject {
        private final String source;

        InMemorySourceFile(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static class InMemoryClassFile extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final String className;
        private final Map<String, byte[]> classBytes;

        InMemoryClassFile(String className, Map<String, byte[]> classBytes) {
            super(URI.create("bytes:///" + className.replace('.', '/') + Kind.CLASS.extension),
                    Kind.CLASS);
            this.className = className;
            this.classBytes = classBytes;
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        void save() {
            classBytes.put(className, outputStream.toByteArray());
        }
    }

    private static class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, byte[]> classBytes;

        InMemoryFileManager(StandardJavaFileManager delegate, Map<String, byte[]> classBytes) {
            super(delegate);
            this.classBytes = classBytes;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                    JavaFileObject.Kind kind, FileObject sibling) {
            var classFile = new InMemoryClassFile(className, classBytes);
            // We need to save bytes when the stream is closed
            return new SimpleJavaFileObject(
                    URI.create("bytes:///" + className.replace('.', '/') + kind.extension), kind) {

                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream() {
                        @Override
                        public void close() {
                            classBytes.put(className, toByteArray());
                        }
                    };
                }
            };
        }
    }
}
