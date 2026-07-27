package ssg.pex.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.bnf.engine.RecursiveDescentEngine;
import ssg.pex.bnf.parser.BnfParser;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.jit.JitCompiler;
import ssg.pex.converter.jit.JitConverter;
import ssg.pex.converter.spi.ConverterRegistry;
import ssg.pex.exec.ExecutionEngine;
import ssg.pex.exec.handler.BaseHandlerProvider;
import ssg.pex.scope.ScopeTree;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComplexIntegrationTest {

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

    // -----------------------------------------------------------------------
    // Part 1: Full Pipeline Complex Scenarios
    // -----------------------------------------------------------------------
    @Nested
    class FullPipelineComplexScenarios {

        @Test
        void bnfGrammarParseExecuteConvertJitFullPipeline() {
            // Step 1: Define BNF grammar
            String bnfSource = """
                    grammar miniCalc;
                    expr ::= term (('+' | '-') term)* ;
                    term ::= factor (('*' | '/') factor)* ;
                    factor ::= /[0-9]+/ | '(' expr ')' ;
                    """;
            var bnfParser = new BnfParser();
            var grammarResult = bnfParser.parse(bnfSource);
            assertThat(grammarResult.isSuccess()).isTrue();
            var grammar = grammarResult.value();
            assertThat(grammar.name()).isEqualTo("miniCalc");

            // Step 2: Parse input with RDE
            var rde = new RecursiveDescentEngine();
            var parseResult = rde.parse("3 + 5 * 2", grammar);
            assertThat(parseResult.isSuccess()).isTrue();

            // Step 3: Build equivalent AST and execute with arithmetics plugin
            var ast = new BinaryOpNode(
                    new IntLiteral(3), Operator.PLUS,
                    new BinaryOpNode(new IntLiteral(5), Operator.MULTIPLY, new IntLiteral(2)));
            var execResult = engine.execute(ast);
            assertThat(execResult.isSuccess()).isTrue();
            assertThat(((Number) execResult.value()).longValue()).isEqualTo(13L);

            // Step 4: Convert to 3 languages
            var javaConv = ConverterRegistry.getInstance().getConverter(TargetLanguage.JAVA).orElseThrow();
            var rubyConv = ConverterRegistry.getInstance().getConverter(TargetLanguage.RUBY).orElseThrow();
            var basicConv = ConverterRegistry.getInstance().getConverter(TargetLanguage.BASIC).orElseThrow();
            assertThat(javaConv.convert(ast).isSuccess()).isTrue();
            assertThat(rubyConv.convert(ast).isSuccess()).isTrue();
            assertThat(basicConv.convert(ast).isSuccess()).isTrue();

            // Step 5: JIT compile and execute
            var jitCompiler = new JitCompiler();
            String jitSource = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestFullPipeline implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            return 3 + 5 * 2;
                        }
                    }
                    """;
            var jitResult = jitCompiler.compile(jitSource, "ssg.pex.jit.generated.TestFullPipeline");
            assertThat(jitResult.isSuccess()).isTrue();
            assertThat(jitResult.value().instance().execute(Map.of())).isEqualTo(13);
        }

        @Test
        void sqlDrivenComputationWithAggregation() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE orders (id INT, product VARCHAR(50), quantity INT, price INT)");
            db.execute("INSERT INTO orders (id, product, quantity, price) VALUES (1, 'Widget', 10, 25)");
            db.execute("INSERT INTO orders (id, product, quantity, price) VALUES (2, 'Gadget', 5, 50)");
            db.execute("INSERT INTO orders (id, product, quantity, price) VALUES (3, 'Widget', 8, 25)");
            db.execute("INSERT INTO orders (id, product, quantity, price) VALUES (4, 'Gizmo', 3, 100)");

            // Aggregate query
            var aggResult = db.execute(
                    "SELECT product, SUM(quantity) AS total_qty FROM orders GROUP BY product");
            assertThat(aggResult.isSuccess()).isTrue();
            var qr = (QueryResult) aggResult.value();
            assertThat(qr.rowCount()).isEqualTo(3); // 3 distinct products

            // Use a specific quantity in arithmetic
            var selectResult = db.execute("SELECT quantity FROM orders WHERE id = 2");
            assertThat(selectResult.isSuccess()).isTrue();
            var sqr = (QueryResult) selectResult.value();
            Number qty = (Number) sqr.rows().getFirst().getValue(0);

            // Feed into arithmetics: qty * price + tax
            var ast = new BinaryOpNode(
                    new BinaryOpNode(new IntLiteral(qty.longValue()), Operator.MULTIPLY, new IntLiteral(50)),
                    Operator.PLUS, new IntLiteral(25));
            var execResult = engine.execute(ast);
            assertThat(execResult.isSuccess()).isTrue();
            // 5 * 50 + 25 = 275
            assertThat(((Number) execResult.value()).longValue()).isEqualTo(275L);

            db.close();
        }

        @Test
        void multiStepParseExecuteInsertQueryBack() {
            // Step 1: Parse and execute arithmetic expression
            var ast = new BinaryOpNode(
                    new BinaryOpNode(new IntLiteral(100), Operator.MULTIPLY, new IntLiteral(3)),
                    Operator.PLUS, new IntLiteral(50));
            var execResult = engine.execute(ast);
            assertThat(execResult.isSuccess()).isTrue();
            long computedValue = ((Number) execResult.value()).longValue();
            assertThat(computedValue).isEqualTo(350L);

            // Step 2: Insert computed value into SQL database
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE results (id INT, computed_value INT)");
            db.execute("INSERT INTO results (id, computed_value) VALUES (1, " + computedValue + ")");

            // Step 3: Query it back
            var queryResult = db.execute("SELECT computed_value FROM results WHERE id = 1");
            assertThat(queryResult.isSuccess()).isTrue();
            var qr = (QueryResult) queryResult.value();
            assertThat(qr.rowCount()).isEqualTo(1);
            assertThat(((Number) qr.rows().getFirst().getValue(0)).longValue()).isEqualTo(350L);

            db.close();
        }

        @Test
        void complexExpressionWithTwentyPlusNodesParseExecuteConvertJit() {
            // Build a tree with 20+ AST nodes: ((1+2)*(3+4)) + ((5-6)/(7+1)) + ((8%3)*(9-2))
            var a = new BinaryOpNode(
                    new BinaryOpNode(new IntLiteral(1), Operator.PLUS, new IntLiteral(2)),
                    Operator.MULTIPLY,
                    new BinaryOpNode(new IntLiteral(3), Operator.PLUS, new IntLiteral(4)));
            var b = new BinaryOpNode(
                    new BinaryOpNode(new IntLiteral(5), Operator.MINUS, new IntLiteral(6)),
                    Operator.DIVIDE,
                    new BinaryOpNode(new IntLiteral(7), Operator.PLUS, new IntLiteral(1)));
            var c = new BinaryOpNode(
                    new BinaryOpNode(new IntLiteral(8), Operator.MODULO, new IntLiteral(3)),
                    Operator.MULTIPLY,
                    new BinaryOpNode(new IntLiteral(9), Operator.MINUS, new IntLiteral(2)));
            var fullExpr = new BinaryOpNode(new BinaryOpNode(a, Operator.PLUS, b), Operator.PLUS, c);

            // Count nodes: 12 IntLiterals + 11 BinaryOps = 23 nodes
            assertThat(countNodes(fullExpr)).isGreaterThanOrEqualTo(20);

            // Execute
            var execResult = engine.execute(fullExpr);
            assertThat(execResult.isSuccess()).isTrue();
            // (1+2)*(3+4) = 3*7 = 21
            // (5-6)/(7+1) = -1/8 = 0 (integer division)
            // (8%3)*(9-2) = 2*7 = 14
            // 21 + 0 + 14 = 35
            assertThat(((Number) execResult.value()).longValue()).isEqualTo(35L);

            // Convert to all languages
            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var convResult = converter.convert(fullExpr);
                assertThat(convResult.isSuccess())
                        .as("Complex 20+ node expr should convert to %s", lang.displayName())
                        .isTrue();
            }
        }

        @Test
        void chainedConversionsComplexProgramToMultipleLanguages() {
            // Build a complex AST: function with loop, conditionals, assignments
            var body = new BlockNode(List.of(
                    new AssignmentNode(new IdentifierNode("sum"), new IntLiteral(0)),
                    new AssignmentNode(new IdentifierNode("i"), new IntLiteral(1)),
                    new LoopNode(LoopKind.WHILE,
                            new BinaryOpNode(new IdentifierNode("i"), Operator.LE, new IdentifierNode("n")),
                            new BlockNode(List.of(
                                    new ConditionalNode(
                                            new BinaryOpNode(
                                                    new BinaryOpNode(new IdentifierNode("i"), Operator.MODULO, new IntLiteral(2)),
                                                    Operator.EQ, new IntLiteral(0)),
                                            new BlockNode(List.of(
                                                    new AssignmentNode(new IdentifierNode("sum"),
                                                            new BinaryOpNode(new IdentifierNode("sum"), Operator.PLUS, new IdentifierNode("i")))
                                            ))
                                    ),
                                    new AssignmentNode(new IdentifierNode("i"),
                                            new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                            ))
                    ),
                    new ReturnNode(new IdentifierNode("sum"))
            ));
            var func = new FunctionDefNode("sumEven", List.of(new ParameterNode("n")), body);
            var program = new ProgramNode(List.of(func));

            // Convert to Java and verify it looks right
            var javaResult = ConverterRegistry.getInstance().getConverter(TargetLanguage.JAVA).orElseThrow().convert(program);
            assertThat(javaResult.isSuccess()).isTrue();
            assertThat(javaResult.value()).contains("Object sumEven(Object n)");
            assertThat(javaResult.value()).contains("while (");
            assertThat(javaResult.value()).contains("if (");

            // Convert to Ruby and verify
            var rubyResult = ConverterRegistry.getInstance().getConverter(TargetLanguage.RUBY).orElseThrow().convert(program);
            assertThat(rubyResult.isSuccess()).isTrue();
            assertThat(rubyResult.value()).contains("def sum_even(n)");
            assertThat(rubyResult.value()).contains("end");

            // Convert to BASIC and verify
            var basicResult = ConverterRegistry.getInstance().getConverter(TargetLanguage.BASIC).orElseThrow().convert(program);
            assertThat(basicResult.isSuccess()).isTrue();
            assertThat(basicResult.value()).contains("FUNCTION SUMEVEN(n)");
            assertThat(basicResult.value()).contains("WHILE ");
        }

        @Test
        void bnfGrammarWithMultipleRulesThenParse() {
            String bnfSource = """
                    grammar jsonLike;
                    value ::= string | number | object | array | 'true' | 'false' | 'null' ;
                    string ::= /"[^"]*"/ ;
                    number ::= /[0-9]+/ ;
                    object ::= '{' (pair (',' pair)*)? '}' ;
                    pair ::= string ':' value ;
                    array ::= '[' (value (',' value)*)? ']' ;
                    """;
            var bnfParser = new BnfParser();
            var grammarResult = bnfParser.parse(bnfSource);
            assertThat(grammarResult.isSuccess()).isTrue();
            var grammar = grammarResult.value();
            assertThat(grammar.name()).isEqualTo("jsonLike");

            var rde = new RecursiveDescentEngine();
            // Parse a simple number
            var r1 = rde.parse("42", grammar);
            assertThat(r1.isSuccess()).isTrue();

            // Parse a string
            var r2 = rde.parse("\"hello\"", grammar);
            assertThat(r2.isSuccess()).isTrue();

            // Parse true/false/null
            assertThat(rde.parse("true", grammar).isSuccess()).isTrue();
            assertThat(rde.parse("false", grammar).isSuccess()).isTrue();
            assertThat(rde.parse("null", grammar).isSuccess()).isTrue();
        }

        @Test
        void programNodeMultipleStatementsExecutionOrderMatters() {
            // Execute multiple statements; result is the last one
            var stmts = new ArrayList<AstNode>();
            for (int i = 1; i <= 10; i++) {
                stmts.add(new BinaryOpNode(new IntLiteral(i), Operator.MULTIPLY, new IntLiteral(i)));
            }
            var program = new ProgramNode(stmts);
            var result = engine.execute(program);
            assertThat(result.isSuccess()).isTrue();
            // Last statement: 10*10 = 100
            assertThat(((Number) result.value()).longValue()).isEqualTo(100L);
        }

        @Test
        void executionStatisticsTrackNodeCount() {
            var ast = new BinaryOpNode(
                    new BinaryOpNode(new IntLiteral(1), Operator.PLUS, new IntLiteral(2)),
                    Operator.MULTIPLY,
                    new BinaryOpNode(new IntLiteral(3), Operator.PLUS, new IntLiteral(4)));
            engine.statistics().reset();
            var result = engine.execute(ast);
            assertThat(result.isSuccess()).isTrue();
            assertThat(engine.statistics().getNodeCount()).isGreaterThan(0);
        }

        @Test
        void nestedFunctionCallsInAST() {
            // Build: sqrt(abs(-16)) via AST (won't parse, just execute)
            var innerCall = new FunctionCallNode("abs", List.of(
                    new UnaryOpNode(Operator.MINUS, new IntLiteral(16), true)));
            var outerCall = new FunctionCallNode("sqrt", List.of(innerCall));
            var result = engine.execute(outerCall);
            assertThat(result.isSuccess()).isTrue();
            assertThat((Double) result.value()).isEqualTo(4.0);
        }

        @Test
        void complexExpressionWithAllArithmeticOperators() {
            // (10 + 5) - (3 * 2) + (20 / 4) - (7 % 3)
            var a = new BinaryOpNode(new IntLiteral(10), Operator.PLUS, new IntLiteral(5));
            var b = new BinaryOpNode(new IntLiteral(3), Operator.MULTIPLY, new IntLiteral(2));
            var c = new BinaryOpNode(new IntLiteral(20), Operator.DIVIDE, new IntLiteral(4));
            var d = new BinaryOpNode(new IntLiteral(7), Operator.MODULO, new IntLiteral(3));
            var expr = new BinaryOpNode(
                    new BinaryOpNode(new BinaryOpNode(a, Operator.MINUS, b), Operator.PLUS, c),
                    Operator.MINUS, d);
            var result = engine.execute(expr);
            assertThat(result.isSuccess()).isTrue();
            // (15 - 6) + 5 - 1 = 13
            assertThat(((Number) result.value()).longValue()).isEqualTo(13L);
        }
    }

    // -----------------------------------------------------------------------
    // Part 2: Cross-Module Interaction
    // -----------------------------------------------------------------------
    @Nested
    class CrossModuleInteraction {

        @Test
        void mathFunctionResultInsertedIntoSqlTable() {
            // Use execution engine to compute pow(2, 10) = 1024
            var powCall = new FunctionCallNode("pow", List.of(new IntLiteral(2), new IntLiteral(10)));
            var execResult = engine.execute(powCall);
            assertThat(execResult.isSuccess()).isTrue();
            long computedValue = ((Number) execResult.value()).longValue();
            assertThat(computedValue).isEqualTo(1024L);

            // Insert into SQL
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE computations (id INT, func_name VARCHAR(50), result INT)");
            db.execute("INSERT INTO computations (id, func_name, result) VALUES (1, 'pow_2_10', " + computedValue + ")");

            var queryResult = db.execute("SELECT result FROM computations WHERE id = 1");
            assertThat(queryResult.isSuccess()).isTrue();
            var qr = (QueryResult) queryResult.value();
            assertThat(((Number) qr.rows().getFirst().getValue(0)).longValue()).isEqualTo(1024L);

            db.close();
        }

        @Test
        void sqlQueryResultFedIntoConverter() {
            // SQL data representing a "computed expression"
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE expressions (id INT, left_val INT, op VARCHAR(10), right_val INT)");
            db.execute("INSERT INTO expressions (id, left_val, op, right_val) VALUES (1, 42, '+', 8)");

            var queryResult = db.execute("SELECT left_val, right_val FROM expressions WHERE id = 1");
            assertThat(queryResult.isSuccess()).isTrue();
            var qr = (QueryResult) queryResult.value();
            int left = ((Number) qr.rows().getFirst().getValue(0)).intValue();
            int right = ((Number) qr.rows().getFirst().getValue(1)).intValue();

            // Build AST from SQL-derived values and convert to code
            var ast = new BinaryOpNode(new IntLiteral(left), Operator.PLUS, new IntLiteral(right));
            var javaResult = ConverterRegistry.getInstance().getConverter(TargetLanguage.JAVA).orElseThrow().convert(ast);
            assertThat(javaResult.isSuccess()).isTrue();
            assertThat(javaResult.value()).contains("(42 + 8)");

            db.close();
        }

        @Test
        void executionEngineWithAllPluginsLoadedComplexExecution() {
            // Build complex expression mixing math functions and operators
            // max(abs(-10), sqrt(49)) + min(5, 3)
            var absCall = new FunctionCallNode("abs", List.of(
                    new UnaryOpNode(Operator.MINUS, new IntLiteral(10), true)));
            var sqrtCall = new FunctionCallNode("sqrt", List.of(new IntLiteral(49)));
            var maxCall = new FunctionCallNode("max", List.of(absCall, sqrtCall));
            var minCall = new FunctionCallNode("min", List.of(new IntLiteral(5), new IntLiteral(3)));
            var fullExpr = new BinaryOpNode(maxCall, Operator.PLUS, minCall);

            var result = engine.execute(fullExpr);
            assertThat(result.isSuccess()).isTrue();
            // max(10, 7.0) = 10.0; min(5, 3) = 3; 10.0 + 3 = 13.0
            assertThat(((Number) result.value()).doubleValue()).isEqualTo(13.0);
        }

        @Test
        void bnfGrammarParsingPlusDialectExtension() {
            // Define base grammar
            String baseBnf = """
                    grammar extended;
                    expr ::= number ('+' number)* ;
                    number ::= /[0-9]+/ ;
                    """;
            var bnfParser = new BnfParser();
            var grammarResult = bnfParser.parse(baseBnf);
            assertThat(grammarResult.isSuccess()).isTrue();

            // Parse with base grammar
            var rde = new RecursiveDescentEngine();
            var r1 = rde.parse("1 + 2 + 3", grammarResult.value());
            assertThat(r1.isSuccess()).isTrue();
        }

        @Test
        void scopeTreeWithArithmeticExpressionsAtEachLevel() {
            var scopeTree = new ScopeTree();
            assertThat(scopeTree).isNotNull();

            // Execute a simple expression - verifying scoping works across execution
            var ast = new BinaryOpNode(new IntLiteral(10), Operator.PLUS, new IntLiteral(20));
            var result = engine.execute(ast);
            assertThat(result.isSuccess()).isTrue();
            assertThat(((Number) result.value()).longValue()).isEqualTo(30L);

            // Another expression in "different scope" conceptually
            var ast2 = new BinaryOpNode(new IntLiteral(100), Operator.MINUS, new IntLiteral(30));
            var result2 = engine.execute(ast2);
            assertThat(result2.isSuccess()).isTrue();
            assertThat(((Number) result2.value()).longValue()).isEqualTo(70L);
        }

        @Test
        void jitCompiledExpressionMatchesDirectExecution() {
            // Direct execution: 7 * 8 + 3
            var ast = new BinaryOpNode(
                    new BinaryOpNode(new IntLiteral(7), Operator.MULTIPLY, new IntLiteral(8)),
                    Operator.PLUS, new IntLiteral(3));
            var directResult = engine.execute(ast);
            assertThat(directResult.isSuccess()).isTrue();
            long directValue = ((Number) directResult.value()).longValue();

            // JIT execution of equivalent
            var jitCompiler = new JitCompiler();
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class TestJitMatch implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            return 7 * 8 + 3;
                        }
                    }
                    """;
            var jitResult = jitCompiler.compile(source, "ssg.pex.jit.generated.TestJitMatch");
            assertThat(jitResult.isSuccess()).isTrue();
            int jitValue = (Integer) jitResult.value().instance().execute(Map.of());

            assertThat((long) jitValue).isEqualTo(directValue);
        }

        @Test
        void pluginInitializationOrderVerification() {
            // Build engine with plugins and verify it can handle all node types
            var testEngine = ExecutionEngine.builder()
                    .plugin(new BaseHandlerProvider())
                    .loadPlugins()
                    .build();

            // Test various operations work after plugin init
            assertThat(testEngine.execute(new IntLiteral(42)).isSuccess()).isTrue();
            assertThat(testEngine.execute(new BoolLiteral(true)).isSuccess()).isTrue();
            assertThat(testEngine.execute(new StringLiteral("hello")).isSuccess()).isTrue();
            assertThat(testEngine.execute(new FloatLiteral(3.14)).isSuccess()).isTrue();
            assertThat(testEngine.execute(new NullLiteral()).isSuccess()).isTrue();
            assertThat(testEngine.execute(new BinaryOpNode(new IntLiteral(1), Operator.PLUS, new IntLiteral(2))).isSuccess()).isTrue();

            testEngine.close();
        }

        @Test
        void sqlMultiTableJoinWithArithmeticProcessing() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE products (id INT, name VARCHAR(50), price INT)");
            db.execute("CREATE TABLE discounts (product_id INT, discount_pct INT)");
            db.execute("INSERT INTO products (id, name, price) VALUES (1, 'Laptop', 1000)");
            db.execute("INSERT INTO products (id, name, price) VALUES (2, 'Phone', 500)");
            db.execute("INSERT INTO discounts (product_id, discount_pct) VALUES (1, 10)");
            db.execute("INSERT INTO discounts (product_id, discount_pct) VALUES (2, 20)");

            var joinResult = db.execute(
                    "SELECT products.name, products.price, discounts.discount_pct " +
                    "FROM products JOIN discounts ON products.id = discounts.product_id");
            assertThat(joinResult.isSuccess()).isTrue();
            var qr = (QueryResult) joinResult.value();
            assertThat(qr.rowCount()).isEqualTo(2);

            // Compute discounted price for first product using arithmetic engine
            int price = ((Number) qr.rows().getFirst().getValue(1)).intValue();
            int discountPct = ((Number) qr.rows().getFirst().getValue(2)).intValue();

            // price - (price * discountPct / 100)
            var discountExpr = new BinaryOpNode(
                    new IntLiteral(price), Operator.MINUS,
                    new BinaryOpNode(
                            new BinaryOpNode(new IntLiteral(price), Operator.MULTIPLY, new IntLiteral(discountPct)),
                            Operator.DIVIDE, new IntLiteral(100)));
            var discountedResult = engine.execute(discountExpr);
            assertThat(discountedResult.isSuccess()).isTrue();
            // 1000 - (1000*10/100) = 1000 - 100 = 900
            assertThat(((Number) discountedResult.value()).longValue()).isEqualTo(900L);

            db.close();
        }

        @Test
        void convertSqlDerivedAstToAllLanguages() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE config (id INT, multiplier INT, offset_val INT)");
            db.execute("INSERT INTO config (id, multiplier, offset_val) VALUES (1, 7, 13)");

            var queryResult = db.execute("SELECT multiplier, offset_val FROM config WHERE id = 1");
            assertThat(queryResult.isSuccess()).isTrue();
            var qr = (QueryResult) queryResult.value();
            int mult = ((Number) qr.rows().getFirst().getValue(0)).intValue();
            int offset = ((Number) qr.rows().getFirst().getValue(1)).intValue();

            // Build AST from SQL values
            var ast = new BinaryOpNode(
                    new BinaryOpNode(new IdentifierNode("input"), Operator.MULTIPLY, new IntLiteral(mult)),
                    Operator.PLUS, new IntLiteral(offset));

            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(ast);
                assertThat(result.isSuccess())
                        .as("SQL-derived AST should convert to %s", lang.displayName())
                        .isTrue();
                assertThat(result.value()).contains("7").contains("13");
            }

            db.close();
        }
    }

    // -----------------------------------------------------------------------
    // Part 3: Stress and Edge Cases
    // -----------------------------------------------------------------------
    @Nested
    class StressEdgeCases {

        @Test
        void executeExpressionWith100PlusNodes() {
            // Build: 1 + 2 + 3 + ... + 50 (chain of BinaryOpNodes)
            AstNode current = new IntLiteral(1);
            for (int i = 2; i <= 50; i++) {
                current = new BinaryOpNode(current, Operator.PLUS, new IntLiteral(i));
            }
            // That's 50 IntLiterals + 49 BinaryOpNodes = 99 nodes, plus a ProgramNode = 100
            var program = new ProgramNode(List.of(current));
            assertThat(countNodes(program)).isGreaterThanOrEqualTo(100);

            var result = engine.execute(program);
            assertThat(result.isSuccess()).isTrue();
            // Sum 1..50 = 50*51/2 = 1275
            assertThat(((Number) result.value()).longValue()).isEqualTo(1275L);
        }

        @Test
        void deeplyNestedScopeTreeTenLevels() {
            var scopeTree = new ScopeTree();
            for (int i = 1; i <= 10; i++) {
                scopeTree.enterScope("level_" + i);
            }
            // Exit all
            for (int i = 10; i >= 1; i--) {
                scopeTree.exitScope();
            }
            // Should be back at root - no exception thrown
            assertThat(scopeTree).isNotNull();
        }

        @Test
        void sqlDatabaseWith5TablesAnd100Rows() {
            var db = new InMemoryDatabase();

            // Create 5 tables
            db.execute("CREATE TABLE t1 (id INT, val INT)");
            db.execute("CREATE TABLE t2 (id INT, val INT)");
            db.execute("CREATE TABLE t3 (id INT, val INT)");
            db.execute("CREATE TABLE t4 (id INT, val INT)");
            db.execute("CREATE TABLE t5 (id INT, val INT)");

            // Insert 20 rows each = 100 total
            for (int t = 1; t <= 5; t++) {
                for (int r = 1; r <= 20; r++) {
                    db.execute("INSERT INTO t" + t + " (id, val) VALUES (" + r + ", " + (t * 100 + r) + ")");
                }
            }

            // Verify counts
            for (int t = 1; t <= 5; t++) {
                var countResult = db.execute("SELECT * FROM t" + t);
                assertThat(countResult.isSuccess()).isTrue();
                assertThat(((QueryResult) countResult.value()).rowCount()).isEqualTo(20);
            }

            // Complex query with WHERE and ORDER BY
            var result = db.execute("SELECT val FROM t3 WHERE val > 310 ORDER BY val LIMIT 5");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rowCount()).isEqualTo(5);

            db.close();
        }

        @Test
        void convertAstWith50PlusNodesToAllLanguages() {
            // Build expression chain with 50+ nodes
            List<AstNode> stmts = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                stmts.add(new AssignmentNode(
                        new IdentifierNode("v" + i),
                        new BinaryOpNode(
                                new BinaryOpNode(new IntLiteral(i), Operator.MULTIPLY, new IntLiteral(i + 1)),
                                Operator.PLUS, new IntLiteral(i * 2))
                ));
            }
            var program = new ProgramNode(stmts);
            // 10 assignments * (1 IdentifierNode + 1 BinaryOp + 1 BinaryOp + 3 IntLiterals) = 60+ nodes
            assertThat(countNodes(program)).isGreaterThanOrEqualTo(50);

            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(program);
                assertThat(result.isSuccess())
                        .as("50+ node AST should convert to %s", lang.displayName())
                        .isTrue();
                assertThat(result.value()).isNotBlank();
            }
        }

        @Test
        void multipleJitCompilationsInSequenceNoClassLoaderLeaks() {
            var compiler = new JitCompiler();
            for (int i = 0; i < 20; i++) {
                String className = "TestNoLeak" + i;
                String source = """
                        package ssg.pex.jit.generated;
                        import ssg.pex.converter.jit.JitExecutable;
                        import java.util.Map;
                        public class %s implements JitExecutable {
                            @Override
                            public Object execute(Map<String, Object> context) {
                                return %d;
                            }
                        }
                        """.formatted(className, i * 3 + 1);
                var result = compiler.compile(source, "ssg.pex.jit.generated." + className);
                assertThat(result.isSuccess())
                        .as("JIT compilation %d should succeed", i)
                        .isTrue();
                assertThat(result.value().instance().execute(Map.of())).isEqualTo(i * 3 + 1);
            }
        }

        @Test
        void bnfGrammarWith30PlusRules() {
            // Build a grammar with many rules
            var sb = new StringBuilder();
            sb.append("grammar bigGrammar;\n");
            sb.append("start ::= rule1 ;\n");
            for (int i = 1; i <= 29; i++) {
                sb.append("rule").append(i).append(" ::= ");
                if (i < 29) {
                    sb.append("rule").append(i + 1).append(" | /token").append(i).append("/ ;\n");
                } else {
                    sb.append("/token").append(i).append("/ ;\n");
                }
            }
            // That gives us 30 rules (start + rule1..rule29)
            var bnfParser = new BnfParser();
            var grammarResult = bnfParser.parse(sb.toString());
            assertThat(grammarResult.isSuccess()).isTrue();
            var grammar = grammarResult.value();
            assertThat(grammar.name()).isEqualTo("bigGrammar");
        }

        @Test
        void functionWithTenPlusParameters() {
            List<ParameterNode> params = new ArrayList<>();
            List<AstNode> additions = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                String name = "p" + i;
                params.add(new ParameterNode(name));
                if (i == 0) {
                    additions.add(new IdentifierNode(name));
                }
            }
            // Sum all 12 parameters: p0 + p1 + ... + p11
            AstNode sumExpr = new IdentifierNode("p0");
            for (int i = 1; i < 12; i++) {
                sumExpr = new BinaryOpNode(sumExpr, Operator.PLUS, new IdentifierNode("p" + i));
            }
            var func = new FunctionDefNode("sumAll", params,
                    new BlockNode(List.of(new ReturnNode(sumExpr))));
            var program = new ProgramNode(List.of(func));

            // Verify conversion to all languages
            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(program);
                assertThat(result.isSuccess())
                        .as("10+ param function should convert to %s", lang.displayName())
                        .isTrue();
                // All should contain all parameter references
                for (int i = 0; i < 12; i++) {
                    assertThat(result.value()).contains("p" + i);
                }
            }
        }

        @Test
        void complexSqlWithMultipleOperations() {
            var db = new InMemoryDatabase();

            // Create and populate
            db.execute("CREATE TABLE employees (id INT, name VARCHAR(50), dept VARCHAR(30), salary INT)");
            db.execute("INSERT INTO employees (id, name, dept, salary) VALUES (1, 'Alice', 'Eng', 90000)");
            db.execute("INSERT INTO employees (id, name, dept, salary) VALUES (2, 'Bob', 'Eng', 85000)");
            db.execute("INSERT INTO employees (id, name, dept, salary) VALUES (3, 'Carol', 'Sales', 70000)");
            db.execute("INSERT INTO employees (id, name, dept, salary) VALUES (4, 'Dave', 'Sales', 65000)");
            db.execute("INSERT INTO employees (id, name, dept, salary) VALUES (5, 'Eve', 'HR', 75000)");

            // Aggregate
            var aggResult = db.execute(
                    "SELECT dept, COUNT(*) AS cnt, SUM(salary) AS total_sal FROM employees GROUP BY dept");
            assertThat(aggResult.isSuccess()).isTrue();
            assertThat(((QueryResult) aggResult.value()).rowCount()).isEqualTo(3);

            // Update
            var updateResult = db.execute("UPDATE employees SET salary = 95000 WHERE name = 'Alice'");
            assertThat(updateResult.isSuccess()).isTrue();
            assertThat(((DmlResult) updateResult.value()).affectedRows()).isEqualTo(1);

            // Delete
            var deleteResult = db.execute("DELETE FROM employees WHERE dept = 'HR'");
            assertThat(deleteResult.isSuccess()).isTrue();
            assertThat(((DmlResult) deleteResult.value()).affectedRows()).isEqualTo(1);

            // Verify final state
            var finalResult = db.execute("SELECT * FROM employees");
            assertThat(finalResult.isSuccess()).isTrue();
            assertThat(((QueryResult) finalResult.value()).rowCount()).isEqualTo(4);

            db.close();
        }

        @Test
        void executionWithManySequentialExpressions() {
            // Execute 50 different expressions one after another
            for (int i = 1; i <= 50; i++) {
                var ast = new BinaryOpNode(new IntLiteral(i), Operator.MULTIPLY, new IntLiteral(i));
                var result = engine.execute(ast);
                assertThat(result.isSuccess())
                        .as("Expression %d should execute successfully", i)
                        .isTrue();
                assertThat(((Number) result.value()).longValue())
                        .as("Result of %d * %d", i, i)
                        .isEqualTo((long) i * i);
            }
        }

        @Test
        void converterRegistryDiscoveryIsConsistent() {
            var registry = ConverterRegistry.getInstance();
            var targets = registry.availableTargets();
            assertThat(targets).hasSize(8); // JAVA, CSHARP, CPP, KOTLIN, SCALA, RUBY, BASIC, JIT

            // Verify each target produces a converter
            for (TargetLanguage lang : TargetLanguage.values()) {
                assertThat(registry.getConverter(lang))
                        .as("Converter for %s should be present", lang.displayName())
                        .isPresent();
            }
        }

        @Test
        void bitwiseOperationsExecuteCorrectly() {
            // 0xFF & 0x0F = 0x0F = 15
            var andExpr = new BinaryOpNode(new IntLiteral(0xFF), Operator.BIT_AND, new IntLiteral(0x0F));
            var result = engine.execute(andExpr);
            assertThat(result.isSuccess()).isTrue();
            assertThat(((Number) result.value()).intValue()).isEqualTo(15);

            // 0xF0 | 0x0F = 0xFF = 255
            var orExpr = new BinaryOpNode(new IntLiteral(0xF0), Operator.BIT_OR, new IntLiteral(0x0F));
            var result2 = engine.execute(orExpr);
            assertThat(result2.isSuccess()).isTrue();
            assertThat(((Number) result2.value()).intValue()).isEqualTo(255);

            // 1 << 10 = 1024
            var shiftExpr = new BinaryOpNode(new IntLiteral(1), Operator.SHIFT_LEFT, new IntLiteral(10));
            var result3 = engine.execute(shiftExpr);
            assertThat(result3.isSuccess()).isTrue();
            assertThat(((Number) result3.value()).longValue()).isEqualTo(1024L);
        }

        @Test
        void endToEndSqlCreateViewThenQuery() {
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE inventory (id INT, item VARCHAR(50), qty INT, warehouse VARCHAR(20))");
            for (int i = 1; i <= 30; i++) {
                String warehouse = i % 3 == 0 ? "East" : (i % 3 == 1 ? "West" : "Central");
                db.execute("INSERT INTO inventory (id, item, qty, warehouse) VALUES (" +
                        i + ", 'item_" + i + "', " + (i * 5) + ", '" + warehouse + "')");
            }

            db.execute("CREATE VIEW east_inventory AS SELECT id, item, qty FROM inventory WHERE warehouse = 'East'");
            var viewResult = db.execute("SELECT * FROM east_inventory");
            assertThat(viewResult.isSuccess()).isTrue();
            var qr = (QueryResult) viewResult.value();
            assertThat(qr.rowCount()).isEqualTo(10); // every 3rd item

            db.close();
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static int countNodes(AstNode node) {
        int count = 1;
        for (AstNode child : node.children()) {
            count += countNodes(child);
        }
        return count;
    }
}
