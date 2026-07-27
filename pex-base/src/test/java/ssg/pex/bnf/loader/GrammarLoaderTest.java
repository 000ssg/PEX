package ssg.pex.bnf.loader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.result.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrammarLoaderTest {

    private final GrammarLoader loader = new GrammarLoader();

    @Nested
    @DisplayName("loadFromResource")
    class LoadFromResource {

        @Test
        @DisplayName("loads a valid grammar from classpath resource")
        void loadsValidGrammar() {
            Result<Grammar> result = loader.loadFromResource("/grammar/test-grammar.ebnf");

            assertThat(result.isSuccess()).isTrue();
            Grammar grammar = result.value();
            assertThat(grammar.name()).isEqualTo("test");
            assertThat(grammar.startRuleName()).isEqualTo("expression");
            assertThat(grammar.rules()).containsKeys("expression", "term", "factor", "number");
            assertThat(grammar.rules()).hasSize(4);
        }

        @Test
        @DisplayName("returns failure for missing resource")
        void failsForMissingResource() {
            Result<Grammar> result = loader.loadFromResource("/grammar/nonexistent.ebnf");

            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("GRAMMAR_NOT_FOUND");
            assertThat(result.error().message()).contains("nonexistent.ebnf");
        }

        @Test
        @DisplayName("returns failure for invalid grammar syntax")
        void failsForInvalidGrammar() {
            Result<Grammar> result = loader.loadFromResource("/grammar/invalid-grammar.ebnf");

            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("BNF_PARSE_ERROR");
        }
    }

    @Nested
    @DisplayName("loadFromFile")
    class LoadFromFile {

        @Test
        @DisplayName("loads a valid grammar from filesystem path")
        void loadsFromFile(@TempDir Path tempDir) throws IOException {
            Path grammarFile = tempDir.resolve("my-grammar.ebnf");
            Files.writeString(grammarFile, """
                    grammar file-test;
                    start ::= 'hello' 'world' ;
                    """);

            Result<Grammar> result = loader.loadFromFile(grammarFile);

            assertThat(result.isSuccess()).isTrue();
            Grammar grammar = result.value();
            assertThat(grammar.name()).isEqualTo("file-test");
            assertThat(grammar.rules()).containsKey("start");
            assertThat(grammar.rules()).hasSize(1);
        }

        @Test
        @DisplayName("returns failure for missing file")
        void failsForMissingFile() {
            Result<Grammar> result = loader.loadFromFile(Path.of("/tmp/does-not-exist-pex.ebnf"));

            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("GRAMMAR_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("loadAndMerge")
    class LoadAndMerge {

        @Test
        @DisplayName("merges multiple grammar resources")
        void mergesMultipleGrammars() {
            Result<Grammar> result = loader.loadAndMerge(
                    "/grammar/test-grammar.ebnf",
                    "/grammar/test-grammar-extra.ebnf");

            assertThat(result.isSuccess()).isTrue();
            Grammar grammar = result.value();
            // Base grammar name is used
            assertThat(grammar.name()).isEqualTo("test");
            // Start rule from base grammar
            assertThat(grammar.startRuleName()).isEqualTo("expression");
            // Contains rules from both grammars
            assertThat(grammar.rules()).containsKeys(
                    "expression", "term", "factor", "number",
                    "boolean-literal", "identifier");
            assertThat(grammar.rules()).hasSize(6);
        }

        @Test
        @DisplayName("returns failure when no paths provided")
        void failsForEmptyPaths() {
            Result<Grammar> result = loader.loadAndMerge();

            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("GRAMMAR_EMPTY");
        }

        @Test
        @DisplayName("returns failure when any resource is missing")
        void failsWhenAnyResourceMissing() {
            Result<Grammar> result = loader.loadAndMerge(
                    "/grammar/test-grammar.ebnf",
                    "/grammar/nonexistent.ebnf");

            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("GRAMMAR_NOT_FOUND");
        }

        @Test
        @DisplayName("works with single resource path")
        void worksWithSinglePath() {
            Result<Grammar> result = loader.loadAndMerge("/grammar/test-grammar.ebnf");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rules()).hasSize(4);
        }
    }
}
