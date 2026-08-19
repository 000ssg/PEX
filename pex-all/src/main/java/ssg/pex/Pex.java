package ssg.pex;

import ssg.pex.ast.node.AssignmentNode;
import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.BlockNode;
import ssg.pex.ast.node.BoolLiteral;
import ssg.pex.ast.node.ConditionalNode;
import ssg.pex.ast.node.FloatLiteral;
import ssg.pex.ast.node.FunctionCallNode;
import ssg.pex.ast.node.FunctionDefNode;
import ssg.pex.ast.node.IdentifierNode;
import ssg.pex.ast.node.IntLiteral;
import ssg.pex.ast.node.Operator;
import ssg.pex.ast.node.ParameterNode;
import ssg.pex.ast.node.ProgramNode;
import ssg.pex.ast.node.ReturnNode;
import ssg.pex.bnf.engine.ParseMatch;
import ssg.pex.bnf.engine.RecursiveDescentEngine;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.parser.BnfParser;
import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.TargetLanguage;
import ssg.pex.converter.jit.JitCompiler;
import ssg.pex.converter.jit.JitConverter;
import ssg.pex.converter.spi.ConverterRegistry;
import ssg.pex.exec.ExecutionEngine;
import ssg.pex.exec.handler.BaseHandlerProvider;
import ssg.pex.arithmetics.ArithmeticsPlugin;
import ssg.pex.result.Result;
import ssg.pex.scope.ScalarVariable;
import ssg.pex.scope.ScopeTree;
import ssg.pex.scope.ShadowRecord;
import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginRegistry;
import ssg.pex.nosql.Document;
import ssg.pex.nosql.InMemoryNoSqlDatabase;
import ssg.pex.nosql.dialects.mongodb.MongoDbDatabase;
import ssg.pex.nosql.dialects.mongodb.MongoQuery;
import ssg.pex.nosql.dialects.mongodb.MongoUpdate;
import ssg.pex.nosql.dialects.mongodb.MongoPipeline;
import ssg.pex.nosql.dialects.cassandra.CassandraDatabase;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.DialectDatabase;
import ssg.pex.sql.dialects.DialectDatabase.DialectType;
import ssg.pex.bnf.loader.GrammarLoader;
import ssg.pex.type.TypeDescriptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive usage demonstration for the PEX framework.
 *
 * <p>Covers all major features: BNF grammar parsing, recursive descent parsing,
 * AST construction and execution, scoped variables, functions, arithmetic
 * operations, SQL operations, code conversion, JIT compilation, and the
 * plugin system.
 */
public final class Pex {

    public static void main(String[] args) {

        // ----------------------------------------------------------------
        // 1. BNF Grammar Parsing
        // ----------------------------------------------------------------
        System.out.println("\n=== 1. BNF Grammar Parsing ===");

        var bnfSource = """
                grammar arithmetic;

                // A simple arithmetic expression grammar
                expr     ::= term ( ( '+' | '-' ) term )* ;
                term     ::= factor ( ( '*' | '/' ) factor )* ;
                factor   ::= /[0-9]+/ | '(' expr ')' ;
                """;

        var parser = new BnfParser();
        Result<Grammar> grammarResult = parser.parse(bnfSource);
        grammarResult.fold(
                grammar -> {
                    System.out.println("Grammar name : " + grammar.name());
                    System.out.println("Start rule   : " + grammar.startRuleName());
                    System.out.println("Rule count   : " + grammar.rules().size());
                    grammar.rules().forEach((name, rule) ->
                            System.out.println("  rule '" + name + "' -> " + rule.body().getClass().getSimpleName()));
                    return null;
                },
                error -> {
                    System.out.println("Grammar parse error: " + error);
                    return null;
                }
        );

        // ----------------------------------------------------------------
        // 2. Recursive Descent Parsing
        // ----------------------------------------------------------------
        System.out.println("\n=== 2. Recursive Descent Parsing ===");

        grammarResult.peek(grammar -> {
            var engine = new RecursiveDescentEngine();

            String input1 = "3 + 5 * 2";
            Result<ParseMatch> match1 = engine.parse(input1, grammar);
            match1.fold(
                    m -> {
                        System.out.println("Input        : " + input1);
                        System.out.println("Matched text : " + m.matchedText());
                        System.out.println("Rule name    : " + m.ruleName());
                        System.out.println("Children     : " + m.children().size());
                        return null;
                    },
                    err -> {
                        System.out.println("Parse failed : " + err);
                        return null;
                    }
            );

            String input2 = "10 + 20 + 30";
            Result<ParseMatch> match2 = engine.parse(input2, grammar);
            match2.fold(
                    m -> {
                        System.out.println("Input        : " + input2);
                        System.out.println("Matched text : " + m.matchedText());
                        return null;
                    },
                    err -> {
                        System.out.println("Parse failed : " + err);
                        return null;
                    }
            );
        });

        // ----------------------------------------------------------------
        // 3. AST Construction & Execution
        // ----------------------------------------------------------------
        System.out.println("\n=== 3. AST Construction & Execution ===");

        // Build AST for: (10 + 20) * 3
        var ast3 = new BinaryOpNode(
                new BinaryOpNode(new IntLiteral(10), Operator.PLUS, new IntLiteral(20)),
                Operator.MULTIPLY,
                new IntLiteral(3)
        );

        try (var engine3 = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .build()) {

            Result<Object> result3 = engine3.execute(ast3);
            result3.fold(
                    val -> { System.out.println("(10 + 20) * 3 = " + val); return null; },
                    err -> { System.out.println("Execution error: " + err); return null; }
            );
        }

        // Boolean expression: 5 > 3
        var boolAst = new BinaryOpNode(new IntLiteral(5), Operator.GT, new IntLiteral(3));

        try (var engine3b = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .build()) {

            engine3b.execute(boolAst).fold(
                    val -> { System.out.println("5 > 3 = " + val); return null; },
                    err -> { System.out.println("Execution error: " + err); return null; }
            );
        }

        // Conditional: if (10 > 5) 42 else 0
        var condAst = new ConditionalNode(
                new BinaryOpNode(new IntLiteral(10), Operator.GT, new IntLiteral(5)),
                new IntLiteral(42),
                new IntLiteral(0)
        );

        try (var engine3c = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .build()) {

            engine3c.execute(condAst).fold(
                    val -> { System.out.println("if (10 > 5) 42 else 0 = " + val); return null; },
                    err -> { System.out.println("Execution error: " + err); return null; }
            );
        }

        // ----------------------------------------------------------------
        // 4. Scoped Variables
        // ----------------------------------------------------------------
        System.out.println("\n=== 4. Scoped Variables ===");

        var scopeTree = new ScopeTree();
        System.out.println("Root scope path: " + scopeTree.currentPath());

        // Define 'x' in global scope
        scopeTree.defineVariable("x",
                new ScalarVariable("x", 100, TypeDescriptor.INT, true));
        System.out.println("Defined x = 100 in " + scopeTree.currentPath());

        // Enter a child scope and shadow 'x'
        scopeTree.enterScope("block1");
        System.out.println("Entered scope : " + scopeTree.currentPath());

        scopeTree.defineVariable("x",
                new ScalarVariable("x", 999, TypeDescriptor.INT, true));
        System.out.println("Defined x = 999 in " + scopeTree.currentPath() + " (shadows global x)");

        // Resolve 'x' -- should find the shadowed value
        scopeTree.resolveVariable("x").peek(v ->
                System.out.println("Resolved x = " + v.currentValue() + " (from " + scopeTree.currentPath() + ")"));

        // Exit back to global scope
        scopeTree.exitScope();
        System.out.println("Exited to     : " + scopeTree.currentPath());

        scopeTree.resolveVariable("x").peek(v ->
                System.out.println("Resolved x = " + v.currentValue() + " (from " + scopeTree.currentPath() + ")"));

        // Show shadow history
        List<ShadowRecord> shadows = scopeTree.shadowHistory();
        System.out.println("Shadow history : " + shadows.size() + " record(s)");
        for (var sr : shadows) {
            System.out.println("  '" + sr.variableName() + "' in " + sr.definingScope()
                    + " shadows " + sr.shadowedScope()
                    + " (was " + sr.previousValue() + ", now " + sr.newValue() + ")");
        }

        // ----------------------------------------------------------------
        // 5. Functions
        // ----------------------------------------------------------------
        System.out.println("\n=== 5. Functions ===");

        // Define: function double(n) { return n * 2; }
        var funcDef = new FunctionDefNode(
                "double",
                List.of(new ParameterNode("n")),
                new BlockNode(List.of(
                        new ReturnNode(
                                new BinaryOpNode(
                                        new IdentifierNode("n"),
                                        Operator.MULTIPLY,
                                        new IntLiteral(2)
                                ))
                ))
        );

        // Call: double(21)
        var funcCall = new FunctionCallNode("double", List.of(new IntLiteral(21)));

        var program5 = new ProgramNode(List.of(funcDef, funcCall));

        try (var engine5 = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .build()) {

            engine5.execute(program5).fold(
                    val -> { System.out.println("double(21) = " + val); return null; },
                    err -> { System.out.println("Function error: " + err); return null; }
            );
        }

        // ----------------------------------------------------------------
        // 6. Arithmetic Operations (with ArithmeticsPlugin)
        // ----------------------------------------------------------------
        System.out.println("\n=== 6. Arithmetic Operations ===");

        try (var engine6 = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .plugin(new ArithmeticsPlugin())
                .build()) {

            // Mixed int/float arithmetic: 7 + 3.5
            var mixedAst = new BinaryOpNode(new IntLiteral(7), Operator.PLUS, new FloatLiteral(3.5));
            engine6.execute(mixedAst).fold(
                    val -> { System.out.println("7 + 3.5 = " + val + " (" + val.getClass().getSimpleName() + ")"); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            // Modulo: 17 % 5
            var modAst = new BinaryOpNode(new IntLiteral(17), Operator.MODULO, new IntLiteral(5));
            engine6.execute(modAst).fold(
                    val -> { System.out.println("17 % 5 = " + val); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            // Bitwise AND: 0xFF & 0x0F
            var bitwiseAst = new BinaryOpNode(new IntLiteral(0xFF), Operator.BIT_AND, new IntLiteral(0x0F));
            engine6.execute(bitwiseAst).fold(
                    val -> { System.out.println("0xFF & 0x0F = " + val); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            // Boolean: true && false
            var boolAndAst = new BinaryOpNode(new BoolLiteral(true), Operator.AND, new BoolLiteral(false));
            engine6.execute(boolAndAst).fold(
                    val -> { System.out.println("true && false = " + val); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            // Math function: sqrt(144)
            var sqrtAst = new FunctionCallNode("sqrt", List.of(new IntLiteral(144)));
            engine6.execute(sqrtAst).fold(
                    val -> { System.out.println("sqrt(144) = " + val); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            // Math function: pow(2, 10)
            var powAst = new FunctionCallNode("pow", List.of(new IntLiteral(2), new IntLiteral(10)));
            engine6.execute(powAst).fold(
                    val -> { System.out.println("pow(2, 10) = " + val); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            // Math function: abs(-42)
            var absAst = new FunctionCallNode("abs", List.of(new IntLiteral(-42)));
            engine6.execute(absAst).fold(
                    val -> { System.out.println("abs(-42) = " + val); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            // Type conversion: int(3.7)
            var convAst = new FunctionCallNode("int", List.of(new FloatLiteral(3.7)));
            engine6.execute(convAst).fold(
                    val -> { System.out.println("int(3.7) = " + val + " (" + val.getClass().getSimpleName() + ")"); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            // Available functions
            var funcNames = engine6.functions().functionNames();
            System.out.println("Registered functions: " + funcNames.size());
        }

        // ----------------------------------------------------------------
        // 7. NoSQL Simulation
        // ----------------------------------------------------------------
        System.out.println("\n=== 7. NoSQL Simulation ===");

        // 7a. InMemoryNoSqlDatabase — core CRUD and query
        System.out.println("--- 7a. InMemoryNoSqlDatabase ---");
        try (var nosqlDb = new InMemoryNoSqlDatabase()) {
            var people = nosqlDb.getCollection("people");

            people.insertOne(Document.of("name", "Alice", "age", 30, "dept", "Engineering"));
            people.insertOne(Document.of("name", "Bob",   "age", 25, "dept", "Sales"));
            people.insertOne(Document.of("name", "Carol", "age", 35, "dept", "Engineering"));
            System.out.println("Inserted 3 documents");

             var seniors = people.find(Document.of("age", Document.of("$gt", 28))).toList();
             System.out.println("age > 28: " + seniors.size() + " doc(s)");

            people.updateMany(
                Document.of("dept", "Engineering"),
                Document.of("$set", Document.of("level", "senior"))
            );
            System.out.println("UpdateMany(dept=Engineering) -> $set level=senior");

            var agg = people.aggregate(List.of(
                Document.of("$match", Document.of("dept", "Engineering")),
                Document.of("$group", Document.of("_id", "$dept",
                                                   "count", Document.of("$sum", 1)))
            )).stream().toList();
            System.out.println("Aggregation result: " + agg);
        }

        // 7b. MongoDB fluent API
        System.out.println("--- 7b. MongoDB Dialect ---");
        try (var mongo = new MongoDbDatabase()) {
            var users = mongo.getMongoCollection("users");

            users.insertOne(Document.of("name", "Dave",  "dept", "Eng", "score", 92));
            users.insertOne(Document.of("name", "Eve",   "dept", "Eng", "score", 75));
            users.insertOne(Document.of("name", "Frank", "dept", "HR",  "score", 80));
            System.out.println("Inserted 3 MongoDB documents");

             var engrs = users.find(MongoQuery.eq("dept", "Eng")).toList();
             System.out.println("dept=Eng: " + engrs.size() + " doc(s)");
 
             var highScore = users.find(
                 MongoQuery.and(MongoQuery.eq("dept", "Eng"), MongoQuery.gte("score", 90))
             ).toList();
             System.out.println("dept=Eng AND score>=90: " + highScore.size() + " doc(s)");
             System.out.println("dept=Eng AND score>=90: " + highScore.size() + " doc(s)");

            users.updateMany(MongoQuery.eq("dept", "Eng"), MongoUpdate.inc("score", 5));
            System.out.println("Incremented score by 5 for all Eng users");

            var pipeline = MongoPipeline.match(MongoQuery.gt("score", 70))
                 .sort("score", -1)
                .limit(2);
            var top = users.aggregate(pipeline).stream().toList();
            System.out.println("Top 2 scorers via pipeline: " + top.size() + " doc(s)");
        }

        // 7c. Cassandra CQL dialect
        System.out.println("--- 7c. Cassandra Dialect ---");
        try (var cass = new CassandraDatabase()) {
            var session = cass.connect("shop");
            session.execute("CREATE TABLE products (id INT PRIMARY KEY, name TEXT, price DOUBLE)");
            session.execute("INSERT INTO products (id, name, price) VALUES (1, 'Widget', 9.99)");
            session.execute("INSERT INTO products (id, name, price) VALUES (2, 'Gadget', 24.99)");
            System.out.println("Inserted 2 Cassandra rows");

            var rs = session.execute("SELECT name, price FROM products WHERE id = 1");
             System.out.println("SELECT id=1: " + rs.rows().stream().findFirst().orElse(null));

            var cheap = session.execute("SELECT * FROM products WHERE price < 15.0 ALLOW FILTERING");
            System.out.println("price < 15: " + cheap.rows().size() + " row(s)");

            var stmt = session.prepare("INSERT INTO products (id, name, price) VALUES (?, ?, ?)");
            session.execute(stmt, 3, "Doohickey", 4.50);
            System.out.println("Prepared stmt inserted Doohickey");
        }

        // ----------------------------------------------------------------
        // 8. SQL Operations
        // ----------------------------------------------------------------
        System.out.println("\n=== 8. SQL Operations ===");

        try (var db = new InMemoryDatabase()) {

            // CREATE TABLE
            db.execute("""
                    CREATE TABLE employees (
                        id     INTEGER PRIMARY KEY,
                        name   VARCHAR(100) NOT NULL,
                        dept   VARCHAR(50),
                        salary DOUBLE
                    )
                    """).fold(
                    ok  -> { System.out.println("CREATE TABLE employees -> OK"); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            db.execute("""
                    CREATE TABLE departments (
                        id   INTEGER PRIMARY KEY,
                        name VARCHAR(50) NOT NULL
                    )
                    """).fold(
                    ok  -> { System.out.println("CREATE TABLE departments -> OK"); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            // INSERT
            db.execute("INSERT INTO departments (id, name) VALUES (1, 'Engineering')");
            db.execute("INSERT INTO departments (id, name) VALUES (2, 'Sales')");

            db.execute("INSERT INTO employees (id, name, dept, salary) VALUES (1, 'Alice',   'Engineering', 95000.0)");
            db.execute("INSERT INTO employees (id, name, dept, salary) VALUES (2, 'Bob',     'Sales',       72000.0)");
            db.execute("INSERT INTO employees (id, name, dept, salary) VALUES (3, 'Charlie', 'Engineering', 110000.0)");
            db.execute("INSERT INTO employees (id, name, dept, salary) VALUES (4, 'Diana',   'Sales',       88000.0)");
            System.out.println("Inserted 4 employees and 2 departments");

            // SELECT with WHERE
            db.execute("SELECT name, salary FROM employees WHERE salary > 80000").fold(
                    obj -> {
                        if (obj instanceof QueryResult qr) {
                            System.out.println("Employees with salary > 80000:");
                            System.out.println("  Columns: " + qr.columnNames());
                            for (var row : qr.rows()) {
                                System.out.println("  " + row);
                            }
                        }
                        return null;
                    },
                    err -> { System.out.println("Query error: " + err); return null; }
            );

            // SELECT with JOIN
            db.execute("SELECT e.name, d.name FROM employees e JOIN departments d ON e.dept = d.name").fold(
                    obj -> {
                        if (obj instanceof QueryResult qr) {
                            System.out.println("Employee-Department JOIN (" + qr.rowCount() + " rows):");
                            for (var row : qr.rows()) {
                                System.out.println("  " + row);
                            }
                        }
                        return null;
                    },
                    err -> { System.out.println("Query error: " + err); return null; }
            );

            // SELECT with GROUP BY
            db.execute("SELECT dept, COUNT(*) FROM employees GROUP BY dept").fold(
                    obj -> {
                        if (obj instanceof QueryResult qr) {
                            System.out.println("Employee count by department:");
                            System.out.println("  Columns: " + qr.columnNames());
                            for (var row : qr.rows()) {
                                System.out.println("  " + row);
                            }
                        }
                        return null;
                    },
                    err -> { System.out.println("Query error: " + err); return null; }
            );

            // Transaction: BEGIN, COMMIT, ROLLBACK
            System.out.println("--- Transaction demo ---");
            db.execute("BEGIN").fold(
                    ok  -> { System.out.println("  BEGIN  -> OK"); return null; },
                    err -> { System.out.println("  BEGIN  -> " + err); return null; }
            );
            db.execute("INSERT INTO employees (id, name, dept, salary) VALUES (5, 'Eve', 'Engineering', 105000.0)").fold(
                    ok  -> { System.out.println("  INSERT -> OK"); return null; },
                    err -> { System.out.println("  INSERT -> " + err); return null; }
            );
            db.execute("ROLLBACK").fold(
                    ok  -> { System.out.println("  ROLLBACK -> OK"); return null; },
                    err -> { System.out.println("  ROLLBACK -> " + err); return null; }
            );

            db.execute("SELECT COUNT(*) FROM employees").fold(
                    obj -> {
                        if (obj instanceof QueryResult qr) {
                            System.out.println("  Row count after rollback: " + qr.rows().getFirst());
                        }
                        return null;
                    },
                    err -> { System.out.println("Query error: " + err); return null; }
            );

            // executeBatch: parse INSERT template once, stream rows lazily
            System.out.println("--- executeBatch demo ---");
            int batchInserted = db.executeBatch(
                    "INSERT INTO employees (id, name, dept, salary) VALUES (?, ?, ?, ?)",
                    java.util.stream.IntStream.rangeClosed(10, 14)
                            .mapToObj(i -> new Object[]{i, "Emp" + i, "Engineering", 70000.0 + i * 1000})
            );
            System.out.println("  Batch inserted " + batchInserted + " rows (stream consumed lazily)");
            System.out.println("  Parse cache size: " + db.parseCacheSize());
        }

        // ----------------------------------------------------------------
        // 8. Code Conversion
        // ----------------------------------------------------------------
        System.out.println("\n=== 9. Code Conversion ===");

        // AST for: function add(a, b) { return a + b; }
        var funcAst = new ProgramNode(List.of(
                new FunctionDefNode(
                        "add",
                        List.of(new ParameterNode("a"), new ParameterNode("b")),
                        new BlockNode(List.of(
                                new ReturnNode(
                                        new BinaryOpNode(
                                                new IdentifierNode("a"),
                                                Operator.PLUS,
                                                new IdentifierNode("b")
                                        ))
                        ))
                )
        ));

        var registry = ConverterRegistry.getInstance();
        System.out.println("Available conversion targets: " + registry.availableTargets());

        for (var target : List.of(TargetLanguage.JAVA, TargetLanguage.KOTLIN,
                TargetLanguage.RUBY, TargetLanguage.CPP, TargetLanguage.BASIC)) {
            registry.getConverter(target).ifPresent(converter -> {
                converter.convert(funcAst).fold(
                        code -> {
                            System.out.println("\n--- " + target.displayName() + " ---");
                            System.out.println(code);
                            return null;
                        },
                        err -> {
                            System.out.println(target.displayName() + " conversion error: " + err);
                            return null;
                        }
                );
            });
        }

        // ----------------------------------------------------------------
        // 9. JIT Compilation
        // ----------------------------------------------------------------
        System.out.println("\n=== 10. JIT Compilation ===");

        // AST for: return (10 + 20) * 3
        var jitExpr = new BinaryOpNode(
                new BinaryOpNode(new IntLiteral(10), Operator.PLUS, new IntLiteral(20)),
                Operator.MULTIPLY,
                new IntLiteral(3)
        );
        var jitAst = new ProgramNode(List.of(new ReturnNode(jitExpr)));

        var jitConverter = new JitConverter(ConversionConfig.defaults());
        jitConverter.convert(jitAst).fold(
                javaSource -> {
                    System.out.println("Generated JIT source:");
                    System.out.println(javaSource);

                    // Compile
                    var compiler = new JitCompiler();
                    String className = "ssg.pex.jit.generated.JitExpr_" + Integer.toHexString(jitAst.hashCode());
                    compiler.compile(javaSource, className).fold(
                            compiled -> {
                                long compTimeUs = TimeUnit.NANOSECONDS.toMicros(compiled.compilationTimeNanos());
                                System.out.println("Compilation time: " + compTimeUs + " us");

                                // Execute the compiled expression
                                Object result = compiled.instance().execute(Map.of());
                                System.out.println("JIT execute: (10 + 20) * 3 = " + result);
                                return null;
                            },
                            err -> {
                                System.out.println("JIT compilation error: " + err);
                                return null;
                            }
                    );
                    return null;
                },
                err -> {
                    System.out.println("JIT conversion error: " + err);
                    return null;
                }
        );

        // ----------------------------------------------------------------
        // 10. Plugin System
        // ----------------------------------------------------------------
        System.out.println("\n=== 11. Plugin System ===");

        var pluginRegistry = PluginRegistry.getInstance();
        pluginRegistry.reload();
        List<PexPlugin> plugins = pluginRegistry.loadPlugins();
        System.out.println("Discovered plugins via ServiceLoader: " + plugins.size());
        for (var plugin : plugins) {
            System.out.println("  [order=" + plugin.loadOrder() + "] " + plugin.name()
                    + " (" + plugin.getClass().getName() + ")");
        }

        // ----------------------------------------------------------------
        // 11. Statistics
        // ----------------------------------------------------------------
        System.out.println("\n=== 12. Execution Statistics ===");

        // Run a program and show statistics
        var statProgram = new ProgramNode(List.of(
                new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10)),
                new AssignmentNode(new IdentifierNode("y"), new IntLiteral(20)),
                new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IdentifierNode("y"))
        ));

        try (var engine11 = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .build()) {

            engine11.execute(statProgram).fold(
                    val -> { System.out.println("x=10, y=20; x+y = " + val); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            var stats = engine11.statistics().snapshot();
            System.out.println("Statistics:");
            stats.forEach((k, v) -> System.out.println("  " + k + " = " + v));
        }

        // ----------------------------------------------------------------
        // 12. GrammarLoader — loading EBNF grammars from resources
        // ----------------------------------------------------------------
        System.out.println("\n=== 13. GrammarLoader ===");

        new GrammarLoader().loadFromResource("grammar/expressions.ebnf").fold(
                grammar -> {
                    System.out.println("Loaded grammar from resource: " + grammar.name());
                    System.out.println("  Rules: " + grammar.rules().size());
                    grammar.rules().keySet().forEach(name ->
                            System.out.println("    - " + name));
                    return null;
                },
                err -> {
                    System.out.println("GrammarLoader error: " + err);
                    return null;
                }
        );

        // ----------------------------------------------------------------
        // NOTE: SQL Feature Compliance across all dialects is validated by
        //   ssg.pex.sql.dialect.SqlFeatureComplianceTest (117 tests).
        //   See docs/COMPLIANCE.md at project root for the full feature matrix.
        // ----------------------------------------------------------------

        // ----------------------------------------------------------------
        // 13. PostgreSQL Dialect
        // ----------------------------------------------------------------
        System.out.println("\n=== 14. PostgreSQL Dialect ===");

        try (var pgDb = new DialectDatabase(new InMemoryDatabase(), DialectType.POSTGRESQL)) {
            pgDb.execute("""
                    CREATE TABLE products (
                        id    INTEGER PRIMARY KEY,
                        name  VARCHAR(100) NOT NULL,
                        price DOUBLE
                    )
                    """).fold(
                    ok  -> { System.out.println("CREATE TABLE products -> OK"); return null; },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            pgDb.execute("INSERT INTO products (id, name, price) VALUES (1, 'Widget', 9.99)");
            pgDb.execute("INSERT INTO products (id, name, price) VALUES (2, 'Gadget', 24.99)");
            pgDb.execute("INSERT INTO products (id, name, price) VALUES (3, 'Doohickey', 4.50)");
            System.out.println("Inserted 3 products");

            // ON CONFLICT upsert
            pgDb.execute("INSERT INTO products (id, name, price) VALUES (1, 'Widget Pro', 12.99) ON CONFLICT (id) DO UPDATE SET name = 'Widget Pro', price = 12.99").fold(
                    ok  -> { System.out.println("ON CONFLICT upsert -> OK"); return null; },
                    err -> { System.out.println("Upsert error: " + err); return null; }
            );

            // ILIKE case-insensitive search
            pgDb.execute("SELECT name, price FROM products WHERE name ILIKE '%widget%'").fold(
                    obj -> {
                        if (obj instanceof QueryResult qr) {
                            System.out.println("ILIKE '%widget%': " + qr.rowCount() + " row(s)");
                            for (var row : qr.rows()) System.out.println("  " + row);
                        }
                        return null;
                    },
                    err -> { System.out.println("Query error: " + err); return null; }
            );

            // PostgreSQL functions
            var pgReg = pgDb.functionRegistry();
            System.out.println("INITCAP('hello world') = " + pgReg.get("INITCAP").apply(List.of("hello world")));
            System.out.println("MD5('test')            = " + pgReg.get("MD5").apply(List.of("test")));
        }

        // ----------------------------------------------------------------
        // 14. MySQL ON DUPLICATE KEY UPDATE (upsert)
        // ----------------------------------------------------------------
        System.out.println("\n=== 15. MySQL ON DUPLICATE KEY UPDATE ===");

        try (var mysqlDb = new DialectDatabase(new InMemoryDatabase(), DialectType.MYSQL)) {
            mysqlDb.execute("CREATE TABLE users (id INTEGER NOT NULL, name VARCHAR(50))");
            mysqlDb.execute("INSERT INTO users (id, name) VALUES (1, 'alice')");
            mysqlDb.execute("INSERT INTO users (id, name) VALUES (2, 'bob')");

            // Upsert: id=1 already exists → should UPDATE name, not insert a second row
            mysqlDb.execute("INSERT INTO users (id, name) VALUES (1, 'alice-updated') ON DUPLICATE KEY UPDATE name = VALUES(name)");

            mysqlDb.execute("SELECT * FROM users").fold(
                    obj -> {
                        if (obj instanceof QueryResult qr) {
                            System.out.println("users after upsert: " + qr.rowCount() + " row(s) (expected 2)");
                            for (var row : qr.rows()) System.out.println("  " + row);
                        }
                        return null;
                    },
                    err -> { System.out.println("Error: " + err); return null; }
            );
        }

        // ----------------------------------------------------------------
        // 15. Oracle NVL / DECODE functions
        // ----------------------------------------------------------------
        System.out.println("\n=== 16. Oracle NVL and DECODE ===");

        try (var oracleDb = new DialectDatabase(new InMemoryDatabase(), DialectType.ORACLE)) {
            oracleDb.execute("CREATE TABLE members (id INTEGER NOT NULL, username VARCHAR(50), role VARCHAR(20))");
            oracleDb.execute("INSERT INTO members (id, username, role) VALUES (1, 'alice', 'admin')");
            oracleDb.execute("INSERT INTO members (id, username, role) VALUES (2, NULL, 'user')");

            // NVL returns the second argument when the first is NULL
            oracleDb.execute("SELECT NVL(username, 'anonymous') AS display_name FROM members").fold(
                    obj -> {
                        if (obj instanceof QueryResult qr) {
                            System.out.println("NVL results:");
                            for (var row : qr.rows()) System.out.println("  display_name=" + row.getValue(0));
                        }
                        return null;
                    },
                    err -> { System.out.println("Error: " + err); return null; }
            );
        }

        // ----------------------------------------------------------------
        // 16. OLAP auto-routing (OVER / ROLLUP detection)
        // ----------------------------------------------------------------
        System.out.println("\n=== 17. OLAP auto-routing ===");

        try (var olap = new ssg.pex.sql.olap.OlapDatabase(new InMemoryDatabase())) {
            olap.execute("CREATE TABLE sales (rep VARCHAR(50), amount INTEGER)");
            olap.execute("INSERT INTO sales VALUES ('Alice', 5000)");
            olap.execute("INSERT INTO sales VALUES ('Bob', 3000)");
            olap.execute("INSERT INTO sales VALUES ('Carol', 7000)");

            // Window function — auto-routed via OVER detection
            olap.executeOlap("SELECT rep, amount, ROW_NUMBER() OVER (ORDER BY amount DESC) AS rn FROM sales").fold(
                    obj -> {
                        if (obj instanceof ssg.pex.sql.dbms.result.QueryResult qr) {
                            System.out.println("ROW_NUMBER() OVER ... → " + qr.rowCount() + " rows");
                        }
                        return null;
                    },
                    err -> { System.out.println("Error: " + err); return null; }
            );

            // ROLLUP — auto-routed via GROUP BY ROLLUP detection
            olap.executeOlap("SELECT rep, SUM(amount) AS total FROM sales GROUP BY ROLLUP(rep)").fold(
                    obj -> {
                        if (obj instanceof ssg.pex.sql.dbms.result.QueryResult qr) {
                            System.out.println("GROUP BY ROLLUP → " + qr.rowCount() + " rows (incl. grand total)");
                        }
                        return null;
                    },
                    err -> { System.out.println("Error: " + err); return null; }
            );
        }

        System.out.println("\n=== Done ===");
    }
}
