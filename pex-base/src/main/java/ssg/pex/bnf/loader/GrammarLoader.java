package ssg.pex.bnf.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.parser.BnfParser;
import ssg.pex.result.Result;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@link Grammar} instances from EBNF grammar files on the classpath or filesystem.
 * Uses {@link BnfParser} internally to parse the grammar text.
 *
 * <p>All methods return {@link Result} for error handling, consistent with PEX conventions.
 */
public final class GrammarLoader {

    private static final Logger log = LoggerFactory.getLogger(GrammarLoader.class);

    private final BnfParser parser = new BnfParser();

    /**
     * Load a grammar from a classpath resource.
     *
     * @param resourcePath the resource path (e.g. "grammar/expressions.ebnf")
     * @return a Result containing the parsed Grammar, or a failure
     */
    public Result<Grammar> loadFromResource(String resourcePath) {
        log.debug("Loading grammar from classpath resource: {}", resourcePath);

        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return Result.failure("GRAMMAR_NOT_FOUND",
                        "Resource not found on classpath: " + resourcePath);
            }
            String source = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return parser.parse(source);
        } catch (IOException e) {
            return Result.failure("GRAMMAR_IO_ERROR",
                    "Failed to read resource: " + resourcePath, e);
        }
    }

    /**
     * Load a grammar from a filesystem path.
     *
     * @param path the path to the grammar file
     * @return a Result containing the parsed Grammar, or a failure
     */
    public Result<Grammar> loadFromFile(Path path) {
        log.debug("Loading grammar from file: {}", path);

        if (!Files.exists(path)) {
            return Result.failure("GRAMMAR_NOT_FOUND",
                    "File not found: " + path);
        }

        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return parser.parse(source);
        } catch (IOException e) {
            return Result.failure("GRAMMAR_IO_ERROR",
                    "Failed to read file: " + path, e);
        }
    }

    /**
     * Load multiple grammars from classpath resources and merge them into one.
     * The first grammar's name and start rule are used as the base; subsequent
     * grammars contribute their rules via {@link Grammar#merge(Grammar)}.
     *
     * @param resourcePaths one or more resource paths to load and merge
     * @return a Result containing the merged Grammar, or the first failure encountered
     */
    public Result<Grammar> loadAndMerge(String... resourcePaths) {
        if (resourcePaths == null || resourcePaths.length == 0) {
            return Result.failure("GRAMMAR_EMPTY",
                    "No resource paths provided for merging");
        }

        log.debug("Loading and merging {} grammar resources", resourcePaths.length);

        Result<Grammar> baseResult = loadFromResource(resourcePaths[0]);
        if (baseResult.isFailure()) {
            return baseResult;
        }

        Grammar merged = baseResult.value();

        for (int i = 1; i < resourcePaths.length; i++) {
            Result<Grammar> next = loadFromResource(resourcePaths[i]);
            if (next.isFailure()) {
                return next;
            }
            merged = merged.merge(next.value());
        }

        log.debug("Merged grammar '{}' has {} rules", merged.name(), merged.rules().size());
        return Result.success(merged);
    }
}
