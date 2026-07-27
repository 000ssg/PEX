package ssg.pex.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.AstNode;
import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.FunctionCallNode;
import ssg.pex.ast.node.IntLiteral;
import ssg.pex.ast.node.Operator;
import ssg.pex.ast.node.ProgramNode;
import ssg.pex.bnf.engine.RecursiveDescentEngine;
import ssg.pex.bnf.parser.BnfParser;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.spi.ConverterRegistry;
import ssg.pex.exec.ExecutionEngine;
import ssg.pex.exec.handler.BaseHandlerProvider;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FullPipelineTest {

    private ExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .loadPlugins()
                .build();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void parseGrammarThenParseInputWithRDE() {
        // Step 1: Define a minimal grammar in BNF notation
        String bnfSource = """
                grammar simpleExpr;
                expr ::= number ('+' number)* ;
                number ::= /[0-9]+/ ;
                """;

        // Step 2: Parse the BNF to get a Grammar
        var bnfParser = new BnfParser();
        var grammarResult = bnfParser.parse(bnfSource);
        assertThat(grammarResult.isSuccess()).isTrue();

        var grammar = grammarResult.value();
        assertThat(grammar.name()).isEqualTo("simpleExpr");
        assertThat(grammar.startRuleName()).isEqualTo("expr");

        // Step 3: Use RecursiveDescentEngine to parse input against the grammar
        var rde = new RecursiveDescentEngine();
        var parseResult = rde.parse("1 + 2 + 3", grammar);
        assertThat(parseResult.isSuccess()).isTrue();
        assertThat(parseResult.value().matchedText().trim()).isEqualTo("1 + 2 + 3");
    }

    @Test
    void executeAstAndConvertToMultipleLanguages() {
        // Build AST: (5 + 3) * 2
        AstNode ast = new BinaryOpNode(
                new BinaryOpNode(new IntLiteral(5), Operator.PLUS, new IntLiteral(3)),
                Operator.MULTIPLY,
                new IntLiteral(2));

        // Execute
        var execResult = engine.execute(ast);
        assertThat(execResult.isSuccess()).isTrue();
        assertThat(((Number) execResult.value()).longValue()).isEqualTo(16L);

        // Convert to Java
        var javaConverter = ConverterRegistry.getInstance()
                .getConverter(TargetLanguage.JAVA).orElseThrow();
        var javaResult = javaConverter.convert(ast);
        assertThat(javaResult.isSuccess()).isTrue();
        assertThat(javaResult.value()).contains("+").contains("*");

        // Convert to Ruby
        var rubyConverter = ConverterRegistry.getInstance()
                .getConverter(TargetLanguage.RUBY).orElseThrow();
        var rubyResult = rubyConverter.convert(ast);
        assertThat(rubyResult.isSuccess()).isTrue();
        assertThat(rubyResult.value()).doesNotContain(";");
    }

    @Test
    void sqlDataFedIntoArithmeticsEngine() {
        // Use SQL for data storage, then use arithmetics engine to process results
        var db = new InMemoryDatabase();
        db.execute("CREATE TABLE metrics (id INT, value INT)");
        db.execute("INSERT INTO metrics (id, value) VALUES (1, 100)");
        db.execute("INSERT INTO metrics (id, value) VALUES (2, 200)");
        db.execute("INSERT INTO metrics (id, value) VALUES (3, 300)");

        // Select raw values
        var selectResult = db.execute("SELECT value FROM metrics WHERE id = 2");
        assertThat(selectResult.isSuccess()).isTrue();
        var qr = (QueryResult) selectResult.value();
        assertThat(qr.rowCount()).isEqualTo(1);

        Number valueFromSql = (Number) qr.rows().getFirst().getValue(0);
        assertThat(valueFromSql).isNotNull();

        // Use the execution engine to compute: value * 3 + 50
        var ast = new BinaryOpNode(
                new BinaryOpNode(new IntLiteral(valueFromSql.longValue()), Operator.MULTIPLY, new IntLiteral(3)),
                Operator.PLUS,
                new IntLiteral(50));

        var execResult = engine.execute(ast);
        assertThat(execResult.isSuccess()).isTrue();
        // 200 * 3 + 50 = 650
        assertThat(((Number) execResult.value()).longValue()).isEqualTo(650L);

        db.close();
    }

    @Test
    void programNodeWithMultipleStatements() {
        // Build a program with multiple arithmetic statements
        // The program returns the result of the last statement
        var stmt1 = new BinaryOpNode(new IntLiteral(10), Operator.PLUS, new IntLiteral(20));
        var stmt2 = new BinaryOpNode(new IntLiteral(100), Operator.MINUS, new IntLiteral(1));
        var program = new ProgramNode(List.of(stmt1, stmt2));

        var result = engine.execute(program);
        assertThat(result.isSuccess()).isTrue();
        // ProgramHandler returns the result of the last statement
        assertThat(((Number) result.value()).longValue()).isEqualTo(99L);
    }

    @Test
    void converterOutputConsistencyAcrossLanguages() {
        // Same AST converted to all languages should succeed and produce non-empty output
        AstNode ast = new BinaryOpNode(
                new IntLiteral(42), Operator.MULTIPLY,
                new BinaryOpNode(new IntLiteral(7), Operator.PLUS, new IntLiteral(3)));

        var registry = ConverterRegistry.getInstance();
        for (TargetLanguage lang : TargetLanguage.values()) {
            var converter = registry.getConverter(lang).orElseThrow();
            var result = converter.convert(ast);
            assertThat(result.isSuccess())
                    .as("Conversion to %s should succeed", lang.displayName())
                    .isTrue();
            assertThat(result.value())
                    .as("Output for %s should contain the number 42", lang.displayName())
                    .contains("42");
        }
    }
}
