package ssg.pex.integration;

import org.junit.jupiter.api.*;
import ssg.pex.ast.node.*;
import ssg.pex.bnf.engine.RecursiveDescentEngine;
import ssg.pex.bnf.parser.BnfParser;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.jit.JitCompiler;
import ssg.pex.converter.spi.ConverterRegistry;
import ssg.pex.exec.ExecutionEngine;
import ssg.pex.exec.handler.BaseHandlerProvider;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.DialectDatabase;
import ssg.pex.sql.olap.OlapDatabase;
import ssg.pex.sql.olap.executor.WindowFunctionExecutor;
import ssg.pex.sql.streaming.engine.StreamEvent;
import ssg.pex.sql.streaming.engine.StreamSimulator;
import ssg.pex.sql.streaming.engine.WindowManager;
import ssg.pex.sql.streaming.engine.StreamAggregator;
import ssg.pex.sql.ast.SqlExpression.AggregateFunction;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-world integration tests that exercise cross-module interaction:
 * BNF parsing -> AST -> execution -> SQL -> OLAP -> streaming -> dialects -> converter -> JIT.
 */
class RealWorldIntegrationTest {

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

    // ==========================================================================
    // Full Pipeline: Parse -> Execute -> Store -> Query -> Convert
    // ==========================================================================

    @Nested
    @DisplayName("Full Pipeline Integration")
    class FullPipelineTests {

        @Test
        @DisplayName("Compute revenue in AST, store in SQL, query back, convert to Java")
        void computeStoreQueryConvert() {
            // Step 1: Build expression: (price * quantity) - discount
            var ast = new BinaryOpNode(
                    new BinaryOpNode(new IntLiteral(150), Operator.MULTIPLY, new IntLiteral(20)),
                    Operator.MINUS, new IntLiteral(500));
            var execResult = engine.execute(ast);
            assertThat(execResult.isSuccess()).isTrue();
            long revenue = ((Number) execResult.value()).longValue();
            assertThat(revenue).isEqualTo(2500L);

            // Step 2: Store in SQL database
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE computed_results (id INTEGER, metric VARCHAR(50), value INTEGER)");
            db.execute("INSERT INTO computed_results (id, metric, value) VALUES (1, 'revenue', " + revenue + ")");

            // Step 3: Query back
            var qResult = db.execute("SELECT value FROM computed_results WHERE metric = 'revenue'");
            assertThat(qResult.isSuccess()).isTrue();
            var qr = (QueryResult) qResult.value();
            assertThat(((Number) qr.rows().getFirst().getValue(0)).longValue()).isEqualTo(2500L);

            // Step 4: Convert the original expression to Java and Ruby
            var javaConv = ConverterRegistry.getInstance().getConverter(TargetLanguage.JAVA).orElseThrow();
            var convResult = javaConv.convert(ast);
            assertThat(convResult.isSuccess()).isTrue();

            var rubyConv = ConverterRegistry.getInstance().getConverter(TargetLanguage.RUBY).orElseThrow();
            assertThat(rubyConv.convert(ast).isSuccess()).isTrue();

            db.close();
        }

        @Test
        @DisplayName("BNF grammar parse -> RDE parse -> build equivalent AST -> execute")
        void bnfParseRdeParseExecute() {
            // Step 1: Define and parse BNF grammar
            String bnf = """
                    grammar calc;
                    expr ::= term (('+' | '-') term)* ;
                    term ::= factor (('*' | '/') factor)* ;
                    factor ::= /[0-9]+/ | '(' expr ')' ;
                    """;
            var bnfParser = new BnfParser();
            var grammarResult = bnfParser.parse(bnf);
            assertThat(grammarResult.isSuccess()).isTrue();

            // Step 2: Parse expression with RDE
            var rde = new RecursiveDescentEngine();
            var parseResult = rde.parse("10 + 20 * 3", grammarResult.value());
            assertThat(parseResult.isSuccess()).isTrue();

            // Step 3: Build equivalent AST and execute
            var ast = new BinaryOpNode(
                    new IntLiteral(10), Operator.PLUS,
                    new BinaryOpNode(new IntLiteral(20), Operator.MULTIPLY, new IntLiteral(3)));
            var execResult = engine.execute(ast);
            assertThat(execResult.isSuccess()).isTrue();
            assertThat(((Number) execResult.value()).longValue()).isEqualTo(70L);
        }

        @Test
        @DisplayName("JIT compile and execute computed expression")
        void jitCompileAndExecute() {
            var jitCompiler = new JitCompiler();
            String source = """
                    package ssg.pex.jit.generated;
                    import ssg.pex.converter.jit.JitExecutable;
                    import java.util.Map;
                    public class RealWorldCalc implements JitExecutable {
                        @Override
                        public Object execute(Map<String, Object> context) {
                            int units = ((Number) context.get("units")).intValue();
                            int price = ((Number) context.get("price")).intValue();
                            int discount = ((Number) context.get("discount")).intValue();
                            return units * price - discount;
                        }
                    }
                    """;
            var jitResult = jitCompiler.compile(source, "ssg.pex.jit.generated.RealWorldCalc");
            assertThat(jitResult.isSuccess()).isTrue();

            var result = jitResult.value().instance().execute(
                    Map.of("units", 50, "price", 100, "discount", 500));
            assertThat(result).isEqualTo(4500);
        }
    }

    // ==========================================================================
    // SQL + OLAP Cross-Module
    // ==========================================================================

    @Nested
    @DisplayName("SQL + OLAP Cross-Module")
    class SqlOlapTests {

        @Test
        @DisplayName("Insert data into SQL, run OLAP window function on result")
        void sqlInsertThenOlapWindowFunction() {
            var olapDb = new OlapDatabase();
            olapDb.execute("CREATE TABLE monthly_sales (id INTEGER, rep VARCHAR, month VARCHAR, revenue INTEGER)");
            olapDb.execute("INSERT INTO monthly_sales VALUES (1, 'Alice', 'Jan', 50000)");
            olapDb.execute("INSERT INTO monthly_sales VALUES (2, 'Alice', 'Feb', 60000)");
            olapDb.execute("INSERT INTO monthly_sales VALUES (3, 'Alice', 'Mar', 55000)");
            olapDb.execute("INSERT INTO monthly_sales VALUES (4, 'Bob', 'Jan', 40000)");
            olapDb.execute("INSERT INTO monthly_sales VALUES (5, 'Bob', 'Feb', 45000)");
            olapDb.execute("INSERT INTO monthly_sales VALUES (6, 'Bob', 'Mar', 48000)");

            // Window function: running total per rep
            var wf = olapDb.parseWindowFunction(
                    "SUM(revenue) OVER (PARTITION BY rep ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)");
            var base = olapDb.execute("SELECT rep, month, revenue FROM monthly_sales");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(6);
            assertThat(result.columnNames()).hasSize(4); // rep, month, revenue, running_sum

            olapDb.close();
        }

        @Test
        @DisplayName("CTE to compute department budgets then MERGE changes")
        void cteThenMerge() {
            var olapDb = new OlapDatabase();
            olapDb.execute("CREATE TABLE budget_current (dept VARCHAR, amount INTEGER)");
            olapDb.execute("INSERT INTO budget_current VALUES ('Eng', 500000)");
            olapDb.execute("INSERT INTO budget_current VALUES ('Sales', 300000)");

            olapDb.execute("CREATE TABLE budget_proposed (dept VARCHAR, amount INTEGER)");
            olapDb.execute("INSERT INTO budget_proposed VALUES ('Eng', 550000)");
            olapDb.execute("INSERT INTO budget_proposed VALUES ('Sales', 320000)");
            olapDb.execute("INSERT INTO budget_proposed VALUES ('HR', 150000)");

            var mergeResult = olapDb.executeMerge(
                    "MERGE INTO budget_current t USING budget_proposed s ON t.dept = s.dept " +
                    "WHEN MATCHED THEN UPDATE SET t.amount = s.amount " +
                    "WHEN NOT MATCHED THEN INSERT (dept, amount) VALUES (s.dept, s.amount)");
            assertThat(mergeResult.isSuccess()).isTrue();
            assertThat(mergeResult.value().affectedRows()).isEqualTo(3); // 2 updates + 1 insert

            olapDb.close();
        }
    }

    // ==========================================================================
    // SQL + Streaming Cross-Module
    // ==========================================================================

    @Nested
    @DisplayName("SQL + Streaming Cross-Module")
    class SqlStreamingTests {

        @Test
        @DisplayName("Stream events, aggregate in window, store results in SQL")
        void streamAggregateThenStore() {
            // Step 1: Stream processing
            var sim = new StreamSimulator();
            sim.executeQuery("CREATE STREAM orders (order_id INTEGER, amount DOUBLE, ts BIGINT)");

            for (int i = 0; i < 5; i++) {
                sim.ingestEvent("orders", new StreamEvent(i * 1000, "o" + i,
                        Map.of("order_id", i + 1, "amount", (i + 1) * 100.0, "ts", (long)(i * 1000))));
            }

            // Step 2: Aggregate using tumbling window
            var aggregator = new StreamAggregator();
            var wm = WindowManager.tumbling(5000);
            for (var event : sim.getStream("orders").peekAll()) {
                wm.assignToWindows(event);
            }
            var closed = wm.getClosedWindows(5000);
            assertThat(closed).hasSize(1);
            Object total = aggregator.aggregate(AggregateFunction.SUM, closed.getFirst().events(), "amount");

            // Step 3: Store in SQL database
            var db = new InMemoryDatabase();
            db.execute("CREATE TABLE order_summaries (window_id INTEGER, total_amount DOUBLE, order_count INTEGER)");
            db.execute("INSERT INTO order_summaries (window_id, total_amount, order_count) VALUES (1, " + total + ", " + closed.getFirst().events().size() + ")");

            var qr = (QueryResult) db.execute("SELECT * FROM order_summaries").value();
            assertThat(qr.rowCount()).isEqualTo(1);

            db.close();
        }

        @Test
        @DisplayName("Stream join, then lookup in SQL dimension table")
        void streamJoinThenSqlLookup() {
            var sim = new StreamSimulator();
            sim.executeQuery("CREATE STREAM clicks (user_id VARCHAR, page VARCHAR, ts BIGINT)");

            sim.ingestEvent("clicks", new StreamEvent(1000, "c1",
                    Map.of("user_id", "u1", "page", "/buy", "ts", 1000L)));
            sim.ingestEvent("clicks", new StreamEvent(2000, "c2",
                    Map.of("user_id", "u2", "page", "/cart", "ts", 2000L)));

            // Enrich with user info from lookup table
            Map<Object, Map<String, Object>> userTable = Map.of(
                    "u1", Map.of("name", "Alice", "tier", "gold"),
                    "u2", Map.of("name", "Bob", "tier", "silver")
            );
            var enriched = sim.lookupJoin("clicks", userTable, "user_id");
            assertThat(enriched).hasSize(2);
            assertThat(enriched.get(0).values()).containsEntry("name", "Alice");
        }
    }

    // ==========================================================================
    // SQL Dialects Cross-Module
    // ==========================================================================

    @Nested
    @DisplayName("SQL Dialects Cross-Module")
    class DialectCrossModuleTests {

        @Test
        @DisplayName("Same query across Oracle, MSSQL, MySQL dialects")
        void sameQueryAcrossDialects() {
            String createSql = "CREATE TABLE products (id INTEGER PRIMARY KEY, name VARCHAR(50), price DOUBLE)";
            String insertSql = "INSERT INTO products (id, name, price) VALUES (1, 'Widget', 29.99)";

            var oracleDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.ORACLE);
            var mssqlDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.MSSQL);
            var mysqlDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.MYSQL);

            for (var db : List.of(oracleDb, mssqlDb, mysqlDb)) {
                db.execute(createSql);
                db.execute(insertSql);
                var result = db.execute("SELECT * FROM products");
                assertThat(result.isSuccess()).isTrue();
                assertThat(((QueryResult) result.value()).rowCount()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("Dialect-specific features: Oracle DUAL + MSSQL TOP + MySQL SHOW")
        void dialectSpecificFeatures() {
            var oracleDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.ORACLE);
            var oracleResult = oracleDb.execute("SELECT 42 FROM DUAL");
            assertThat(oracleResult.isSuccess()).isTrue();
            assertThat(((QueryResult) oracleResult.value()).rows().getFirst().getValue(0)).isEqualTo(42L);

            var mssqlDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.MSSQL);
            mssqlDb.execute("CREATE TABLE items (id INTEGER, name VARCHAR(50))");
            mssqlDb.execute("INSERT INTO items (id, name) VALUES (1, 'A')");
            mssqlDb.execute("INSERT INTO items (id, name) VALUES (2, 'B')");
            mssqlDb.execute("INSERT INTO items (id, name) VALUES (3, 'C')");
            var mssqlResult = mssqlDb.execute("SELECT TOP 2 * FROM items");
            assertThat(mssqlResult.isSuccess()).isTrue();
            assertThat(((QueryResult) mssqlResult.value()).rowCount()).isEqualTo(2);

            var mysqlDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.MYSQL);
            mysqlDb.execute("CREATE TABLE data (id INTEGER)");
            var mysqlResult = mysqlDb.execute("SHOW TABLES");
            assertThat(mysqlResult.isSuccess()).isTrue();
            assertThat(((QueryResult) mysqlResult.value()).rowCount()).isGreaterThanOrEqualTo(1);
        }
    }

    // ==========================================================================
    // Converter: Complex AST to Multiple Languages
    // ==========================================================================

    @Nested
    @DisplayName("Multi-Language Conversion")
    class ConverterTests {

        @Test
        @DisplayName("Complex program with function, loop, conditional converts to all languages")
        void complexProgramConvertsToAllLanguages() {
            var body = new BlockNode(List.of(
                    new AssignmentNode(new IdentifierNode("total"), new IntLiteral(0)),
                    new AssignmentNode(new IdentifierNode("i"), new IntLiteral(1)),
                    new LoopNode(LoopKind.WHILE,
                            new BinaryOpNode(new IdentifierNode("i"), Operator.LE, new IdentifierNode("n")),
                            new BlockNode(List.of(
                                    new AssignmentNode(new IdentifierNode("total"),
                                            new BinaryOpNode(new IdentifierNode("total"), Operator.PLUS, new IdentifierNode("i"))),
                                    new AssignmentNode(new IdentifierNode("i"),
                                            new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                            ))
                    ),
                    new ReturnNode(new IdentifierNode("total"))
            ));
            var func = new FunctionDefNode("sumTo", List.of(new ParameterNode("n")), body);
            var program = new ProgramNode(List.of(func));

            for (TargetLanguage lang : TargetLanguage.values()) {
                var converter = ConverterRegistry.getInstance().getConverter(lang).orElseThrow();
                var result = converter.convert(program);
                assertThat(result.isSuccess())
                        .as("Program should convert to %s", lang.displayName())
                        .isTrue();
                assertThat(result.value()).isNotBlank();
            }
        }
    }

    // ==========================================================================
    // Stress / Scale Tests
    // ==========================================================================

    @Nested
    @DisplayName("Stress and Scale Tests")
    class StressTests {

        @Test
        @DisplayName("Insert 100 rows into SQL, aggregate, then apply OLAP window function")
        void hundredRowsWithOlap() {
            var olapDb = new OlapDatabase();
            olapDb.execute("CREATE TABLE big_data (id INTEGER, category VARCHAR, value INTEGER)");
            for (int i = 1; i <= 100; i++) {
                String cat = switch (i % 4) {
                    case 0 -> "A";
                    case 1 -> "B";
                    case 2 -> "C";
                    default -> "D";
                };
                olapDb.execute("INSERT INTO big_data VALUES (" + i + ", '" + cat + "', " + (i * 10) + ")");
            }

            // Verify all inserted
            var selectResult = olapDb.execute("SELECT * FROM big_data");
            assertThat(selectResult.isSuccess()).isTrue();
            assertThat(((QueryResult) selectResult.value()).rowCount()).isEqualTo(100);

            // Apply RANK window function
            var wf = olapDb.parseWindowFunction("RANK() OVER (PARTITION BY category ORDER BY value DESC)");
            var base = olapDb.execute("SELECT category, value FROM big_data");
            var baseQr = (QueryResult) base.value();
            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(100);

            olapDb.close();
        }

        @Test
        @DisplayName("Ingest 50 stream events across 2 streams, join, and aggregate")
        void fiftyEventsStreamJoinAggregate() {
            var sim = new StreamSimulator();
            sim.executeQuery("CREATE STREAM orders (order_id INTEGER, customer_id VARCHAR, amount DOUBLE, ts BIGINT)");
            sim.executeQuery("CREATE STREAM payments (payment_id INTEGER, customer_id VARCHAR, status VARCHAR, ts BIGINT)");

            for (int i = 0; i < 25; i++) {
                String cid = "C" + (i % 5 + 1);
                sim.ingestEvent("orders", new StreamEvent(i * 1000, "o" + i,
                        Map.of("order_id", i, "customer_id", cid, "amount", (i + 1) * 50.0, "ts", (long)(i * 1000))));
            }
            for (int i = 0; i < 25; i++) {
                String cid = "C" + (i % 5 + 1);
                sim.ingestEvent("payments", new StreamEvent(i * 1000 + 500, "p" + i,
                        Map.of("payment_id", i, "customer_id", cid, "status", "paid", "ts", (long)(i * 1000 + 500))));
            }

            assertThat(sim.getStream("orders").peekAll()).hasSize(25);
            assertThat(sim.getStream("payments").peekAll()).hasSize(25);

            var joined = sim.joinStreams("orders", "payments", "customer_id", 2000);
            assertThat(joined).isNotEmpty();
        }
    }
}
