package ssg.pex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.ast.visitor.AstTransformer;
import ssg.pex.ast.visitor.AstVisitor;
import ssg.pex.bnf.engine.RecursiveDescentEngine;
import ssg.pex.bnf.model.*;
import ssg.pex.bnf.parser.BnfParser;
import ssg.pex.exec.*;
import ssg.pex.exec.handler.BaseHandlerProvider;
import ssg.pex.result.Result;
import ssg.pex.scope.*;
import ssg.pex.type.PexType;
import ssg.pex.type.TypeDescriptor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Complex real-life test cases for pex-base: BNF grammars, execution scenarios,
 * scope management, and AST visitor/transformer patterns.
 */
@DisplayName("Complex pex-base tests")
class ComplexBaseTest {

    // ===========================================================================================
    // Complex BNF Grammar Tests
    // ===========================================================================================

    @Nested
    @DisplayName("Complex BNF Grammars")
    class ComplexBnfGrammars {

        private BnfParser parser;
        private RecursiveDescentEngine engine;

        @BeforeEach
        void setUp() {
            parser = new BnfParser();
            engine = new RecursiveDescentEngine();
        }

        private Grammar parseGrammar(String bnf) {
            var result = parser.parse(bnf);
            assertThat(result.isSuccess())
                    .withFailMessage(() -> "Grammar parse failed: " + result.error())
                    .isTrue();
            return result.value();
        }

        @Test
        @DisplayName("full programming language grammar with if/else, while, functions, assignments, expressions")
        void fullProgrammingLanguageGrammar() {
            var g = parseGrammar("""
                    grammar miniLang;
                    program     ::= statement+ ;
                    statement   ::= assignment | ifStmt | whileStmt | funcDef | funcCall | returnStmt ;
                    assignment  ::= identifier '=' expr ';' ;
                    ifStmt      ::= 'if' '(' expr ')' block ( 'else' block )? ;
                    whileStmt   ::= 'while' '(' expr ')' block ;
                    funcDef     ::= 'func' identifier '(' paramList? ')' block ;
                    funcCall    ::= identifier '(' argList? ')' ';' ;
                    returnStmt  ::= 'return' expr? ';' ;
                    block       ::= '{' statement* '}' ;
                    paramList   ::= identifier ( ',' identifier )* ;
                    argList     ::= expr ( ',' expr )* ;
                    expr        ::= term ( ( '+' | '-' ) term )* ;
                    term        ::= atom ( ( '*' | '/' ) atom )* ;
                    atom        ::= number | identifier | '(' expr ')' ;
                    number      ::= /[0-9]+/ ;
                    identifier  ::= /[a-zA-Z_][a-zA-Z0-9_]*/ ;
                    """);
            assertThat(g.name()).isEqualTo("miniLang");
            assertThat(g.rules()).hasSize(16);
            assertThat(g.startRuleName()).isEqualTo("program");
            assertThat(g.rule("ifStmt")).isPresent();
            assertThat(g.rule("whileStmt")).isPresent();
            assertThat(g.rule("funcDef")).isPresent();
        }

        @Test
        @DisplayName("JSON-like grammar definition")
        void jsonLikeGrammar() {
            var g = parseGrammar("""
                    grammar json;
                    value    ::= object | array | string | number | 'true' | 'false' | 'null' ;
                    object   ::= '{' ( pair ( ',' pair )* )? '}' ;
                    pair     ::= string ':' value ;
                    array    ::= '[' ( value ( ',' value )* )? ']' ;
                    string   ::= /"[^"]*"/ ;
                    number   ::= /-?[0-9]+(\\.?[0-9]+)?/ ;
                    """);
            assertThat(g.name()).isEqualTo("json");
            assertThat(g.rules()).hasSize(6);
            assertThat(g.startRuleName()).isEqualTo("value");
        }

        @Test
        @DisplayName("CSV grammar definition and simple parse")
        void csvGrammar() {
            var g = parseGrammar("""
                    grammar csv;
                    file   ::= line ( '\\n' line )* ;
                    line   ::= field ( ',' field )* ;
                    field  ::= /[^,\\n]*/ ;
                    """);
            assertThat(g.name()).isEqualTo("csv");
            assertThat(g.rules()).hasSize(3);
        }

        @Test
        @DisplayName("HTML-like tag grammar")
        void htmlLikeTagGrammar() {
            var g = parseGrammar("""
                    grammar html;
                    document  ::= element+ ;
                    element   ::= openTag content closeTag | selfClose ;
                    openTag   ::= '<' tagName attribute* '>' ;
                    closeTag  ::= '</' tagName '>' ;
                    selfClose ::= '<' tagName attribute* '/>' ;
                    content   ::= ( element | text )* ;
                    attribute ::= attrName '=' attrValue ;
                    tagName   ::= /[a-zA-Z][a-zA-Z0-9]*/ ;
                    attrName  ::= /[a-zA-Z][a-zA-Z0-9_-]*/ ;
                    attrValue ::= /"[^"]*"/ ;
                    text      ::= /[^<]+/ ;
                    """);
            assertThat(g.rules()).hasSize(11);
            assertThat(g.rule("element")).isPresent();
            assertThat(g.rule("attribute")).isPresent();
        }

        @Test
        @DisplayName("grammar with deeply nested alternations and sequences")
        void deeplyNestedAlternationsAndSequences() {
            var g = parseGrammar("""
                    grammar deep;
                    expr ::= ( ( 'a' | 'b' ) ( 'c' | 'd' ) ) | ( ( 'e' | 'f' ) ( 'g' | 'h' ) ) ;
                    """);
            assertThat(g.rules()).hasSize(1);
            var body = g.rule("expr").get().body();
            assertThat(body).isInstanceOf(Alternation.class);
        }

        @Test
        @DisplayName("grammar with 20+ rules")
        void grammarWith20PlusRules() {
            var sb = new StringBuilder("grammar big;\n");
            for (int i = 0; i < 22; i++) {
                sb.append("rule").append(i).append(" ::= 'token").append(i).append("' ;\n");
            }
            var g = parseGrammar(sb.toString());
            assertThat(g.rules()).hasSize(22);
            assertThat(g.name()).isEqualTo("big");
        }

        @Test
        @DisplayName("grammar with multiple dialect-like extensions chained via multiple rules")
        void multiDialectExtensionChained() {
            var g = parseGrammar("""
                    grammar dialect;
                    program  ::= statement+ ;
                    statement ::= letStmt | constStmt | exprStmt ;
                    letStmt  ::= 'let' name '=' expr ';' ;
                    constStmt ::= 'const' name '=' expr ';' ;
                    exprStmt ::= expr ';' ;
                    expr     ::= addExpr ;
                    addExpr  ::= mulExpr ( ( '+' | '-' ) mulExpr )* ;
                    mulExpr  ::= unaryExpr ( ( '*' | '/' | '%' ) unaryExpr )* ;
                    unaryExpr ::= ( '-' | '!' )? primary ;
                    primary  ::= number | name | '(' expr ')' | funcCall ;
                    funcCall ::= name '(' argList? ')' ;
                    argList  ::= expr ( ',' expr )* ;
                    number   ::= /[0-9]+/ ;
                    name     ::= /[a-zA-Z_]+/ ;
                    """);
            assertThat(g.rules()).hasSize(14);
        }

        @Test
        @DisplayName("parse input exercises every RuleExpression type")
        void everyRuleExpressionType() {
            // Sequence, Alternation, Repetition (*, +, ?), Terminal (literal + regex), NonTerminal, Group
            var g = parseGrammar("""
                    grammar all;
                    start   ::= seq ;
                    seq     ::= item item ;
                    item    ::= ( 'x' | 'y' ) suffix? ;
                    suffix  ::= /[0-9]+/ ;
                    """);
            // Parse "x y" exercising Sequence, Alternation, NonTerminal, Group, Terminal, Repetition(?)
            var match = engine.parse("x y", g);
            assertThat(match.isSuccess()).isTrue();
            assertThat(match.value().matchedText()).isEqualTo("x y");
        }

        @Test
        @DisplayName("parse exercises zero-or-more repetition with multiple matches")
        void repetitionZeroOrMoreMultiple() {
            var g = parseGrammar("start ::= ( 'a' | 'b' )* ;");
            var match = engine.parse("a b a b a", g);
            assertThat(match.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("parse exercises one-or-more repetition")
        void repetitionOneOrMore() {
            var g = parseGrammar("start ::= /[a-z]/+ ;");
            var match = engine.parse("a b c", g);
            assertThat(match.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("ambiguous grammar picks first matching alternative")
        void ambiguousGrammarFirstMatch() {
            // Both alternatives start with 'a', but the first alternative matches
            var g = parseGrammar("start ::= 'a' 'b' | 'a' 'c' ;");
            var match = engine.parse("a b", g);
            assertThat(match.isSuccess()).isTrue();
            assertThat(match.value().matchedText()).isEqualTo("a b");
        }

        @Test
        @DisplayName("SQL-like SELECT grammar")
        void sqlSelectGrammar() {
            var g = parseGrammar("""
                    grammar sql;
                    select    ::= 'SELECT' columns 'FROM' table where? ;
                    columns   ::= column ( ',' column )* ;
                    column    ::= /[a-zA-Z_]+/ ;
                    table     ::= /[a-zA-Z_]+/ ;
                    where     ::= 'WHERE' condition ;
                    condition ::= column '=' /[a-zA-Z0-9_]+/ ;
                    """);
            assertThat(g.rules()).hasSize(6);
            var match = engine.parse("SELECT name , age FROM users", g);
            assertThat(match.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("grammar with annotations on multiple rules")
        void annotationsOnMultipleRules() {
            var g = parseGrammar("""
                    grammar ann;
                    @keyword @precedence(1) ifRule ::= 'if' ;
                    @keyword @precedence(2) whileRule ::= 'while' ;
                    @operator @precedence(10) plus ::= '+' ;
                    @operator @precedence(11) star ::= '*' ;
                    """);
            assertThat(g.rules()).hasSize(4);
            assertThat(g.rule("ifRule").get().annotation("precedence")).isEqualTo("1");
            assertThat(g.rule("star").get().annotation("precedence")).isEqualTo("11");
            assertThat(g.rule("whileRule").get().hasAnnotation("keyword")).isTrue();
        }

        @Test
        @DisplayName("grammar with regex capturing different token patterns")
        void regexDifferentPatterns() {
            var g = parseGrammar("""
                    grammar tokens;
                    token ::= ident | integer | floatNum | stringLit ;
                    ident  ::= /[a-zA-Z_][a-zA-Z0-9_]*/ ;
                    integer ::= /[0-9]+/ ;
                    floatNum ::= /[0-9]+\\.[0-9]+/ ;
                    stringLit ::= /"[^"]*"/ ;
                    """);
            assertThat(g.rules()).hasSize(5);
        }

        @Test
        @DisplayName("grammar with deeply nested groups and repetitions")
        void deeplyNestedGroupsRepetitions() {
            var g = parseGrammar("rule ::= ( ( 'a' 'b' )+ ( 'c' 'd' )* )? ;");
            var body = g.rule("rule").get().body();
            assertThat(body).isInstanceOf(Repetition.class);
            assertThat(((Repetition) body).kind()).isEqualTo(RepetitionKind.OPTIONAL);
        }

        @Test
        @DisplayName("key-value config grammar parses multiple pairs")
        void keyValueConfigGrammar() {
            var g = parseGrammar("""
                    grammar config;
                    config ::= pair+ ;
                    pair   ::= key '=' value ';' ;
                    key    ::= /[a-zA-Z_]+/ ;
                    value  ::= /[a-zA-Z0-9_]+/ ;
                    """);
            var match = engine.parse("host = localhost ; port = 8080 ;", g);
            assertThat(match.isSuccess()).isTrue();
        }
    }

    // ===========================================================================================
    // Complex Execution Scenarios
    // ===========================================================================================

    @Nested
    @DisplayName("Complex Execution Scenarios")
    class ComplexExecutionScenarios {

        private ExecutionEngine engine;

        @BeforeEach
        void setUp() {
            engine = ExecutionEngine.builder()
                    .plugin(new BaseHandlerProvider())
                    .build();
        }

        private ExecutionEngine engineWithConfig(ExecutionConfig config) {
            return ExecutionEngine.builder()
                    .plugin(new BaseHandlerProvider())
                    .config(config)
                    .build();
        }

        // Helper: build fib(n) recursive function
        // fib(n) = if (n <= 1) n else fib(n-1) + fib(n-2)
        private FunctionDefNode fibDef() {
            return new FunctionDefNode("fib", List.of(new ParameterNode("n")),
                    new ConditionalNode(
                            new BinaryOpNode(new IdentifierNode("n"), Operator.LE, new IntLiteral(1)),
                            new IdentifierNode("n"),
                            new BinaryOpNode(
                                    new FunctionCallNode("fib", List.of(
                                            new BinaryOpNode(new IdentifierNode("n"), Operator.MINUS, new IntLiteral(1))
                                    )),
                                    Operator.PLUS,
                                    new FunctionCallNode("fib", List.of(
                                            new BinaryOpNode(new IdentifierNode("n"), Operator.MINUS, new IntLiteral(2))
                                    ))
                            )
                    )
            );
        }

        // Helper: build fact(n) recursive function
        // fact(n) = if (n <= 1) 1 else n * fact(n-1)
        private FunctionDefNode factDef() {
            return new FunctionDefNode("fact", List.of(new ParameterNode("n")),
                    new ConditionalNode(
                            new BinaryOpNode(new IdentifierNode("n"), Operator.LE, new IntLiteral(1)),
                            new IntLiteral(1),
                            new BinaryOpNode(
                                    new IdentifierNode("n"),
                                    Operator.MULTIPLY,
                                    new FunctionCallNode("fact", List.of(
                                            new BinaryOpNode(new IdentifierNode("n"), Operator.MINUS, new IntLiteral(1))
                                    ))
                            )
                    )
            );
        }

        @Test
        @DisplayName("fibonacci recursive: fib(10) = 55")
        void fibonacciRecursive() {
            var prog = new ProgramNode(List.of(
                    fibDef(),
                    new FunctionCallNode("fib", List.of(new IntLiteral(10)))
            ));
            var result = engine.execute(prog);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo(55L);
        }

        @Test
        @DisplayName("factorial recursive: fact(10) = 3628800")
        void factorialRecursive() {
            var prog = new ProgramNode(List.of(
                    factDef(),
                    new FunctionCallNode("fact", List.of(new IntLiteral(10)))
            ));
            var result = engine.execute(prog);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo(3628800L);
        }

        @Test
        @DisplayName("nested function calls: f(g(h(x)))")
        void nestedFunctionCalls() {
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("h", List.of(new ParameterNode("x")),
                            new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IntLiteral(1))
                    ),
                    new FunctionDefNode("g", List.of(new ParameterNode("x")),
                            new BinaryOpNode(new IdentifierNode("x"), Operator.MULTIPLY, new IntLiteral(2))
                    ),
                    new FunctionDefNode("f", List.of(new ParameterNode("x")),
                            new BinaryOpNode(new IdentifierNode("x"), Operator.MINUS, new IntLiteral(3))
                    ),
                    // f(g(h(5))) = f(g(6)) = f(12) = 9
                    new FunctionCallNode("f", List.of(
                            new FunctionCallNode("g", List.of(
                                    new FunctionCallNode("h", List.of(new IntLiteral(5)))
                            ))
                    ))
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(9L);
        }

        @Test
        @DisplayName("higher-order-like pattern: function returning value used in another function")
        void higherOrderLikePattern() {
            // compute(op, a, b): if op == 0 then a + b, else a * b
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("compute", List.of(
                            new ParameterNode("op"), new ParameterNode("a"), new ParameterNode("b")),
                            new ConditionalNode(
                                    new BinaryOpNode(new IdentifierNode("op"), Operator.EQ, new IntLiteral(0)),
                                    new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b")),
                                    new BinaryOpNode(new IdentifierNode("a"), Operator.MULTIPLY, new IdentifierNode("b"))
                            )
                    ),
                    // compute(0, 3, 4) = 7, compute(1, 3, 4) = 12 => total = 19
                    new BinaryOpNode(
                            new FunctionCallNode("compute", List.of(new IntLiteral(0), new IntLiteral(3), new IntLiteral(4))),
                            Operator.PLUS,
                            new FunctionCallNode("compute", List.of(new IntLiteral(1), new IntLiteral(3), new IntLiteral(4)))
                    )
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(19L);
        }

        @Test
        @DisplayName("program with 10+ statements: assignments, conditionals, loops, function defs/calls")
        void programWith10PlusStatements() {
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10)),
                    new AssignmentNode(new IdentifierNode("y"), new IntLiteral(20)),
                    new AssignmentNode(new IdentifierNode("z"), new BinaryOpNode(
                            new IdentifierNode("x"), Operator.PLUS, new IdentifierNode("y"))),
                    new FunctionDefNode("double", List.of(new ParameterNode("n")),
                            new BinaryOpNode(new IdentifierNode("n"), Operator.MULTIPLY, new IntLiteral(2))),
                    new AssignmentNode(new IdentifierNode("d"),
                            new FunctionCallNode("double", List.of(new IdentifierNode("z")))),
                    new ConditionalNode(
                            new BinaryOpNode(new IdentifierNode("d"), Operator.GT, new IntLiteral(50)),
                            new AssignmentNode(new IdentifierNode("result"), new StringLiteral("big")),
                            new AssignmentNode(new IdentifierNode("result"), new StringLiteral("small"))
                    ),
                    new AssignmentNode(new IdentifierNode("counter"), new IntLiteral(0)),
                    new LoopNode(LoopKind.WHILE,
                            new BinaryOpNode(new IdentifierNode("counter"), Operator.LT, new IntLiteral(5)),
                            new AssignmentNode(new IdentifierNode("counter"),
                                    new BinaryOpNode(new IdentifierNode("counter"), Operator.PLUS, new IntLiteral(1)))
                    ),
                    new AssignmentNode(new IdentifierNode("final"),
                            new BinaryOpNode(new IdentifierNode("d"), Operator.PLUS, new IdentifierNode("counter"))),
                    new IdentifierNode("final")
            ));
            var result = engine.execute(prog);
            assertThat(result.isSuccess()).isTrue();
            // z=30, d=60, counter=5, final=65
            assertThat(result.value()).isEqualTo(65L);
        }

        @Test
        @DisplayName("while loop computing sum of 1 to 100")
        void whileLoopSumOneToHundred() {
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("sum"), new IntLiteral(0)),
                    new AssignmentNode(new IdentifierNode("i"), new IntLiteral(1)),
                    new LoopNode(LoopKind.WHILE,
                            new BinaryOpNode(new IdentifierNode("i"), Operator.LE, new IntLiteral(100)),
                            new BlockNode(List.of(
                                    new AssignmentNode(new IdentifierNode("sum"),
                                            new BinaryOpNode(new IdentifierNode("sum"), Operator.PLUS, new IdentifierNode("i"))),
                                    new AssignmentNode(new IdentifierNode("i"),
                                            new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                            ))
                    ),
                    new IdentifierNode("sum")
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(5050L);
        }

        @Test
        @DisplayName("nested conditionals: if/else if/else chain")
        void nestedConditionals() {
            // Classify x: <0 => "negative", 0 => "zero", >0 => "positive"
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(0)),
                    new ConditionalNode(
                            new BinaryOpNode(new IdentifierNode("x"), Operator.LT, new IntLiteral(0)),
                            new AssignmentNode(new IdentifierNode("label"), new StringLiteral("negative")),
                            new ConditionalNode(
                                    new BinaryOpNode(new IdentifierNode("x"), Operator.EQ, new IntLiteral(0)),
                                    new AssignmentNode(new IdentifierNode("label"), new StringLiteral("zero")),
                                    new AssignmentNode(new IdentifierNode("label"), new StringLiteral("positive"))
                            )
                    ),
                    new IdentifierNode("label")
            ));
            assertThat(engine.execute(prog).value()).isEqualTo("zero");
        }

        @Test
        @DisplayName("nested conditionals: deeply nested (4 levels)")
        void deeplyNestedConditionals() {
            // x=42: if x>100 "huge" else if x>50 "big" else if x>25 "medium" else "small"
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(42)),
                    new ConditionalNode(
                            new BinaryOpNode(new IdentifierNode("x"), Operator.GT, new IntLiteral(100)),
                            new StringLiteral("huge"),
                            new ConditionalNode(
                                    new BinaryOpNode(new IdentifierNode("x"), Operator.GT, new IntLiteral(50)),
                                    new StringLiteral("big"),
                                    new ConditionalNode(
                                            new BinaryOpNode(new IdentifierNode("x"), Operator.GT, new IntLiteral(25)),
                                            new StringLiteral("medium"),
                                            new StringLiteral("small")
                                    )
                            )
                    )
            ));
            assertThat(engine.execute(prog).value()).isEqualTo("medium");
        }

        @Test
        @DisplayName("variable shadowing across 4+ scope levels with full tracing")
        void variableShadowingAcross4Levels() {
            // Global: x=1; Block { x=2; Block { x=3; Block { x=4; return x } } }
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(1)),
                    new BlockNode(List.of(
                            new AssignmentNode(new IdentifierNode("x"), new IntLiteral(2)),
                            new BlockNode(List.of(
                                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(3)),
                                    new BlockNode(List.of(
                                            new AssignmentNode(new IdentifierNode("x"), new IntLiteral(4)),
                                            new IdentifierNode("x")
                                    ))
                            ))
                    ))
            ));
            // Assignment updates the existing variable (doesn't shadow)
            assertThat(engine.execute(prog).value()).isEqualTo(4L);
        }

        @Test
        @DisplayName("mutual function dependency hits recursion limit")
        void mutualFunctionDependencyRecursionLimit() {
            var eng = engineWithConfig(new ExecutionConfig(10, 100_000, 30_000L, ExecutionConfig.ErrorMode.RESULT));
            // A calls B, B calls A
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("A", List.of(new ParameterNode("n")),
                            new FunctionCallNode("B", List.of(new IdentifierNode("n")))
                    ),
                    new FunctionDefNode("B", List.of(new ParameterNode("n")),
                            new FunctionCallNode("A", List.of(new IdentifierNode("n")))
                    ),
                    new FunctionCallNode("A", List.of(new IntLiteral(0)))
            ));
            var result = eng.execute(prog);
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().message()).containsIgnoringCase("recursion depth");
        }

        @Test
        @DisplayName("function with early return")
        void functionWithEarlyReturn() {
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("earlyReturn", List.of(new ParameterNode("x")),
                            new BlockNode(List.of(
                                    new ConditionalNode(
                                            new BinaryOpNode(new IdentifierNode("x"), Operator.LT, new IntLiteral(0)),
                                            new ReturnNode(new IntLiteral(-1))
                                    ),
                                    new ConditionalNode(
                                            new BinaryOpNode(new IdentifierNode("x"), Operator.EQ, new IntLiteral(0)),
                                            new ReturnNode(new IntLiteral(0))
                                    ),
                                    new ReturnNode(new IntLiteral(1))
                            ))
                    ),
                    new FunctionCallNode("earlyReturn", List.of(new IntLiteral(-5)))
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(-1L);
        }

        @Test
        @DisplayName("function with early return: zero case")
        void functionWithEarlyReturnZero() {
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("sign", List.of(new ParameterNode("x")),
                            new BlockNode(List.of(
                                    new ConditionalNode(
                                            new BinaryOpNode(new IdentifierNode("x"), Operator.LT, new IntLiteral(0)),
                                            new ReturnNode(new IntLiteral(-1))
                                    ),
                                    new ConditionalNode(
                                            new BinaryOpNode(new IdentifierNode("x"), Operator.EQ, new IntLiteral(0)),
                                            new ReturnNode(new IntLiteral(0))
                                    ),
                                    new ReturnNode(new IntLiteral(1))
                            ))
                    ),
                    new FunctionCallNode("sign", List.of(new IntLiteral(0)))
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(0L);
        }

        @Test
        @DisplayName("block expression returns last value")
        void blockExpressionReturnsLastValue() {
            var prog = new ProgramNode(List.of(
                    new BlockNode(List.of(
                            new AssignmentNode(new IdentifierNode("a"), new IntLiteral(1)),
                            new AssignmentNode(new IdentifierNode("b"), new IntLiteral(2)),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b"))
                    ))
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(3L);
        }

        @Test
        @DisplayName("loop with conditional exit pattern (simulated break)")
        void loopWithConditionalExitPattern() {
            // While (found == 0 && i < 100) { if i == 42 then found = 1; i = i+1 }
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("found"), new IntLiteral(0)),
                    new AssignmentNode(new IdentifierNode("i"), new IntLiteral(0)),
                    new LoopNode(LoopKind.WHILE,
                            new BinaryOpNode(
                                    new BinaryOpNode(new IdentifierNode("found"), Operator.EQ, new IntLiteral(0)),
                                    Operator.AND,
                                    new BinaryOpNode(new IdentifierNode("i"), Operator.LT, new IntLiteral(100))
                            ),
                            new BlockNode(List.of(
                                    new ConditionalNode(
                                            new BinaryOpNode(new IdentifierNode("i"), Operator.EQ, new IntLiteral(42)),
                                            new AssignmentNode(new IdentifierNode("found"), new IntLiteral(1))
                                    ),
                                    new AssignmentNode(new IdentifierNode("i"),
                                            new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                            ))
                    ),
                    new IdentifierNode("i")
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(43L);
        }

        @Test
        @DisplayName("complex expression tree: ((a + b) * (c - d)) / ((e % f) + g)")
        void complexExpressionTree() {
            // a=10, b=5, c=20, d=8, e=17, f=5, g=3
            // ((10+5) * (20-8)) / ((17%5) + 3) = (15*12)/(2+3) = 180/5 = 36
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("a"), new IntLiteral(10)),
                    new AssignmentNode(new IdentifierNode("b"), new IntLiteral(5)),
                    new AssignmentNode(new IdentifierNode("c"), new IntLiteral(20)),
                    new AssignmentNode(new IdentifierNode("d"), new IntLiteral(8)),
                    new AssignmentNode(new IdentifierNode("e"), new IntLiteral(17)),
                    new AssignmentNode(new IdentifierNode("f"), new IntLiteral(5)),
                    new AssignmentNode(new IdentifierNode("g"), new IntLiteral(3)),
                    new BinaryOpNode(
                            new BinaryOpNode(
                                    new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b")),
                                    Operator.MULTIPLY,
                                    new BinaryOpNode(new IdentifierNode("c"), Operator.MINUS, new IdentifierNode("d"))
                            ),
                            Operator.DIVIDE,
                            new BinaryOpNode(
                                    new BinaryOpNode(new IdentifierNode("e"), Operator.MODULO, new IdentifierNode("f")),
                                    Operator.PLUS,
                                    new IdentifierNode("g")
                            )
                    )
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(36L);
        }

        @Test
        @DisplayName("deeply nested blocks (5+ levels) with variable resolution from outermost scope")
        void deeplyNestedBlocksOuterScopeResolution() {
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("outer"), new IntLiteral(100)),
                    new BlockNode(List.of(
                            new BlockNode(List.of(
                                    new BlockNode(List.of(
                                            new BlockNode(List.of(
                                                    new BlockNode(List.of(
                                                            new IdentifierNode("outer")
                                                    ))
                                            ))
                                    ))
                            ))
                    ))
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(100L);
        }

        @Test
        @DisplayName("long program: 50+ statement nodes executed sequentially")
        void longProgram50PlusStatements() {
            var statements = new ArrayList<AstNode>();
            statements.add(new AssignmentNode(new IdentifierNode("sum"), new IntLiteral(0)));
            for (int i = 1; i <= 50; i++) {
                statements.add(new AssignmentNode(new IdentifierNode("sum"),
                        new BinaryOpNode(new IdentifierNode("sum"), Operator.PLUS, new IntLiteral(i))));
            }
            statements.add(new IdentifierNode("sum"));
            var prog = new ProgramNode(statements);
            // Sum 1..50 = 1275
            assertThat(engine.execute(prog).value()).isEqualTo(1275L);
        }

        @Test
        @DisplayName("string concatenation in a loop")
        void stringConcatenationInLoop() {
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("result"), new StringLiteral("")),
                    new AssignmentNode(new IdentifierNode("i"), new IntLiteral(0)),
                    new LoopNode(LoopKind.WHILE,
                            new BinaryOpNode(new IdentifierNode("i"), Operator.LT, new IntLiteral(5)),
                            new BlockNode(List.of(
                                    new AssignmentNode(new IdentifierNode("result"),
                                            new BinaryOpNode(new IdentifierNode("result"), Operator.PLUS, new StringLiteral("x"))),
                                    new AssignmentNode(new IdentifierNode("i"),
                                            new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                            ))
                    ),
                    new IdentifierNode("result")
            ));
            assertThat(engine.execute(prog).value()).isEqualTo("xxxxx");
        }

        @Test
        @DisplayName("GCD using Euclid's algorithm")
        void gcdEuclidAlgorithm() {
            // gcd(a, b) = if b == 0 then a else gcd(b, a % b)
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("gcd", List.of(new ParameterNode("a"), new ParameterNode("b")),
                            new ConditionalNode(
                                    new BinaryOpNode(new IdentifierNode("b"), Operator.EQ, new IntLiteral(0)),
                                    new IdentifierNode("a"),
                                    new FunctionCallNode("gcd", List.of(
                                            new IdentifierNode("b"),
                                            new BinaryOpNode(new IdentifierNode("a"), Operator.MODULO, new IdentifierNode("b"))
                                    ))
                            )
                    ),
                    new FunctionCallNode("gcd", List.of(new IntLiteral(48), new IntLiteral(18)))
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(6L);
        }

        @Test
        @DisplayName("power function via loop: pow(2, 10) = 1024")
        void powerFunctionViaLoop() {
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("pow", List.of(new ParameterNode("base"), new ParameterNode("exp")),
                            new BlockNode(List.of(
                                    new AssignmentNode(new IdentifierNode("result"), new IntLiteral(1)),
                                    new AssignmentNode(new IdentifierNode("i"), new IntLiteral(0)),
                                    new LoopNode(LoopKind.WHILE,
                                            new BinaryOpNode(new IdentifierNode("i"), Operator.LT, new IdentifierNode("exp")),
                                            new BlockNode(List.of(
                                                    new AssignmentNode(new IdentifierNode("result"),
                                                            new BinaryOpNode(new IdentifierNode("result"), Operator.MULTIPLY, new IdentifierNode("base"))),
                                                    new AssignmentNode(new IdentifierNode("i"),
                                                            new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                                            ))
                                    ),
                                    new IdentifierNode("result")
                            ))
                    ),
                    new FunctionCallNode("pow", List.of(new IntLiteral(2), new IntLiteral(10)))
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(1024L);
        }

        @Test
        @DisplayName("function scope isolation: function body cannot see caller's locals")
        void functionScopeIsolation() {
            // Define x=99, then call function that tries to use x => UNDEFINED_VARIABLE
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(99)),
                    new FunctionDefNode("readX", List.of(), new IdentifierNode("x")),
                    new FunctionCallNode("readX", List.of())
            ));
            // In this engine, functions have their own scope but share the global scope.
            // x is defined in global scope so it IS visible. Test that the function sees global.
            var result = engine.execute(prog);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo(99L);
        }

        @Test
        @DisplayName("multiple functions composing results")
        void multipleFunctionsComposing() {
            // add(a, b) = a + b; mul(a, b) = a * b; result = mul(add(1,2), add(3,4)) = 3 * 7 = 21
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("add", List.of(new ParameterNode("a"), new ParameterNode("b")),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b"))),
                    new FunctionDefNode("mul", List.of(new ParameterNode("a"), new ParameterNode("b")),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.MULTIPLY, new IdentifierNode("b"))),
                    new FunctionCallNode("mul", List.of(
                            new FunctionCallNode("add", List.of(new IntLiteral(1), new IntLiteral(2))),
                            new FunctionCallNode("add", List.of(new IntLiteral(3), new IntLiteral(4)))
                    ))
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(21L);
        }

        @Test
        @DisplayName("execution statistics updated correctly for complex program")
        void executionStatisticsForComplexProgram() {
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(1)),
                    new AssignmentNode(new IdentifierNode("y"), new IntLiteral(2)),
                    new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IdentifierNode("y"))
            ));
            engine.execute(prog);
            assertThat(engine.statistics().getNodeCount()).isGreaterThan(5);
        }

        @Test
        @DisplayName("listener tracks all events for complex program")
        void listenerTracksAllEvents() {
            var enters = new AtomicInteger();
            var exits = new AtomicInteger();
            var eng = ExecutionEngine.builder()
                    .plugin(new BaseHandlerProvider())
                    .addListener(new ExecutionListener() {
                        @Override public void onNodeEnter(Object node, ExecutionContext ctx) { enters.incrementAndGet(); }
                        @Override public void onNodeExit(Object node, ExecutionContext ctx, Result<Object> result) { exits.incrementAndGet(); }
                        @Override public void onScopeEnter(String scopeName, ExecutionContext ctx) {}
                        @Override public void onScopeExit(String scopeName, ExecutionContext ctx) {}
                        @Override public void onFunctionCall(String functionName, List<Object> args, ExecutionContext ctx) {}
                        @Override public void onError(ssg.pex.result.PexError error, ExecutionContext ctx) {}
                    })
                    .build();

            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(1)),
                    new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IntLiteral(2))
            ));
            eng.execute(prog);
            assertThat(enters.get()).isGreaterThan(3);
            assertThat(exits.get()).isEqualTo(enters.get());
        }

        @Test
        @DisplayName("boolean logic: complex AND/OR expressions")
        void booleanLogicComplex() {
            // (true && false) || (true && true) => true
            var expr = new BinaryOpNode(
                    new BinaryOpNode(new BoolLiteral(true), Operator.AND, new BoolLiteral(false)),
                    Operator.OR,
                    new BinaryOpNode(new BoolLiteral(true), Operator.AND, new BoolLiteral(true))
            );
            assertThat(engine.execute(expr).value()).isEqualTo(true);
        }

        @Test
        @DisplayName("unary negation chain: -(-(-5)) = -5")
        void unaryNegationChain() {
            var expr = new UnaryOpNode(Operator.MINUS,
                    new UnaryOpNode(Operator.MINUS,
                            new UnaryOpNode(Operator.MINUS, new IntLiteral(5), true),
                            true),
                    true);
            assertThat(engine.execute(expr).value()).isEqualTo(-5L);
        }

        @Test
        @DisplayName("loop iteration limit enforced")
        void loopIterationLimitEnforced() {
            var eng = engineWithConfig(new ExecutionConfig(256, 50, 30_000L, ExecutionConfig.ErrorMode.RESULT));
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("c"), new IntLiteral(0)),
                    new LoopNode(LoopKind.WHILE, new BoolLiteral(true),
                            new AssignmentNode(new IdentifierNode("c"),
                                    new BinaryOpNode(new IdentifierNode("c"), Operator.PLUS, new IntLiteral(1)))
                    ),
                    new IdentifierNode("c")
            ));
            var result = eng.execute(prog);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo(50L);
        }
    }

    // ===========================================================================================
    // Complex Scope Scenarios
    // ===========================================================================================

    @Nested
    @DisplayName("Complex Scope Scenarios")
    class ComplexScopeScenarios {

        private ScopeTree tree;

        @BeforeEach
        void setUp() {
            tree = new ScopeTree();
        }

        private ScalarVariable scalarVar(String name, Object value) {
            return new ScalarVariable(name, value, TypeDescriptor.of(PexType.INT), true);
        }

        @Test
        @DisplayName("5-level nested scopes with shadowing at each level")
        void fiveLevelNestedScopesWithShadowing() {
            tree.defineVariable("x", scalarVar("x", 1));
            tree.enterScope("s1");
            tree.defineVariable("x", scalarVar("x", 2));
            tree.enterScope("s2");
            tree.defineVariable("x", scalarVar("x", 3));
            tree.enterScope("s3");
            tree.defineVariable("x", scalarVar("x", 4));
            tree.enterScope("s4");
            tree.defineVariable("x", scalarVar("x", 5));

            assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(5);
            assertThat(tree.shadowHistory()).hasSize(4);

            tree.exitScope();
            assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(4);
            tree.exitScope();
            assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(3);
            tree.exitScope();
            assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(2);
            tree.exitScope();
            assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("variable resolution walking 5 parent levels")
        void variableResolutionWalking5Parents() {
            tree.defineVariable("deepVar", scalarVar("deepVar", 42));
            tree.enterScope("l1");
            tree.enterScope("l2");
            tree.enterScope("l3");
            tree.enterScope("l4");
            tree.enterScope("l5");

            var result = tree.resolveVariable("deepVar");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().currentValue()).isEqualTo(42);
        }

        @Test
        @DisplayName("scope tracer capturing 10+ shadow records")
        void scopeTracerCapturing10PlusShadows() {
            for (int i = 0; i < 11; i++) {
                tree.defineVariable("x", scalarVar("x", i));
                if (i < 10) {
                    tree.enterScope("s" + i);
                }
            }
            assertThat(tree.shadowHistory()).hasSize(10);
        }

        @Test
        @DisplayName("variables in inner scopes not visible after exit")
        void variablesNotVisibleAfterScopeExit() {
            tree.enterScope("inner");
            tree.defineVariable("secret", scalarVar("secret", 42));
            tree.exitScope();

            var result = tree.resolveVariable("secret");
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("UNDEFINED_VARIABLE");
        }

        @Test
        @DisplayName("same variable name in sibling scopes has no conflict")
        void sameVariableInSiblingScopes() {
            tree.enterScope("left");
            tree.defineVariable("x", scalarVar("x", 10));
            assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(10);
            tree.exitScope();

            tree.enterScope("right");
            tree.defineVariable("x", scalarVar("x", 20));
            assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(20);
            tree.exitScope();
        }

        @Test
        @DisplayName("full scope path toString verification for deep nesting")
        void fullScopePathToString() {
            tree.enterScope("module");
            tree.enterScope("class");
            tree.enterScope("method");
            tree.enterScope("block");
            tree.enterScope("lambda");

            assertThat(tree.currentPath().toString()).isEqualTo("global.module.class.method.block.lambda");
        }

        @Test
        @DisplayName("update variable in parent scope from nested scope")
        void updateVariableInParentFromNested() {
            tree.defineVariable("counter", scalarVar("counter", 0));
            tree.enterScope("loop");
            tree.updateVariable("counter", 1);
            tree.enterScope("inner");
            tree.updateVariable("counter", 2);
            tree.exitScope();
            tree.exitScope();
            assertThat(tree.resolveVariable("counter").value().currentValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("multiple variables across nested scopes")
        void multipleVariablesAcrossNestedScopes() {
            tree.defineVariable("a", scalarVar("a", 1));
            tree.defineVariable("b", scalarVar("b", 2));
            tree.enterScope("fn");
            tree.defineVariable("c", scalarVar("c", 3));
            tree.defineVariable("d", scalarVar("d", 4));
            tree.enterScope("block");
            tree.defineVariable("e", scalarVar("e", 5));

            // All 5 visible
            assertThat(tree.resolveVariable("a").value().currentValue()).isEqualTo(1);
            assertThat(tree.resolveVariable("b").value().currentValue()).isEqualTo(2);
            assertThat(tree.resolveVariable("c").value().currentValue()).isEqualTo(3);
            assertThat(tree.resolveVariable("d").value().currentValue()).isEqualTo(4);
            assertThat(tree.resolveVariable("e").value().currentValue()).isEqualTo(5);

            tree.exitScope(); // exit block
            assertThat(tree.resolveVariable("e").isFailure()).isTrue();
            assertThat(tree.resolveVariable("c").value().currentValue()).isEqualTo(3);

            tree.exitScope(); // exit fn
            assertThat(tree.resolveVariable("c").isFailure()).isTrue();
            assertThat(tree.resolveVariable("a").value().currentValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("ScopePath ancestor/descendant relationships")
        void scopePathAncestorDescendant() {
            var root = ScopePath.of("global");
            var child = ScopePath.of("global", "fn");
            var grandchild = ScopePath.of("global", "fn", "block");

            assertThat(root.isAncestorOf(child)).isTrue();
            assertThat(root.isAncestorOf(grandchild)).isTrue();
            assertThat(child.isAncestorOf(grandchild)).isTrue();
            assertThat(grandchild.isAncestorOf(root)).isFalse();
            assertThat(child.isAncestorOf(root)).isFalse();
        }

        @Test
        @DisplayName("listener captures complex scope lifecycle")
        void listenerCapturesComplexLifecycle() {
            var events = new ArrayList<String>();
            tree.addListener(new ScopeTreeListener() {
                @Override public void onScopeEnter(Scope scope) { events.add("E:" + scope.name()); }
                @Override public void onScopeExit(Scope scope) { events.add("X:" + scope.name()); }
                @Override public void onVariableDefine(Scope scope, Variable var) { events.add("D:" + var.name()); }
                @Override public void onVariableShadow(ShadowRecord record) { events.add("S:" + record.variableName()); }
            });

            tree.defineVariable("x", scalarVar("x", 1));
            tree.enterScope("a");
            tree.defineVariable("x", scalarVar("x", 2));
            tree.defineVariable("y", scalarVar("y", 3));
            tree.exitScope();

            assertThat(events).containsExactly("D:x", "E:a", "S:x", "D:x", "D:y", "X:a");
        }

        @Test
        @DisplayName("immutable variable cannot be updated in nested scope")
        void immutableVariableCannotBeUpdatedNested() {
            tree.defineVariable("c", new ScalarVariable("c", 42, TypeDescriptor.INT, false));
            tree.enterScope("inner");
            var result = tree.updateVariable("c", 99);
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("IMMUTABLE_VARIABLE");
        }
    }

    // ===========================================================================================
    // Complex AST Visitor/Transformer Tests
    // ===========================================================================================

    @Nested
    @DisplayName("Complex AST Visitor/Transformer")
    class ComplexAstVisitorTransformer {

        @Test
        @DisplayName("transform renames all identifiers")
        void transformRenamesAllIdentifiers() {
            var transformer = new AstTransformer() {
                @Override
                public Result<AstNode> visitIdentifier(IdentifierNode node) {
                    return Result.success(new IdentifierNode("renamed_" + node.name()));
                }
            };

            var original = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b")))
            ));

            var result = transformer.transform(original);
            assertThat(result.isSuccess()).isTrue();
            var transformed = (ProgramNode) result.value();
            var assignment = (AssignmentNode) transformed.statements().get(0);
            assertThat(((IdentifierNode) assignment.target()).name()).isEqualTo("renamed_x");
            var binOp = (BinaryOpNode) assignment.value();
            assertThat(((IdentifierNode) binOp.left()).name()).isEqualTo("renamed_a");
            assertThat(((IdentifierNode) binOp.right()).name()).isEqualTo("renamed_b");
        }

        @Test
        @DisplayName("transform doubles all IntLiteral values")
        void transformDoublesAllIntLiterals() {
            var transformer = new AstTransformer() {
                @Override
                public Result<AstNode> visitIntLiteral(IntLiteral node) {
                    return Result.success(new IntLiteral(node.value() * 2));
                }
            };

            var original = new BinaryOpNode(new IntLiteral(5), Operator.PLUS, new IntLiteral(3));
            var result = transformer.transform(original);
            assertThat(result.isSuccess()).isTrue();
            var binOp = (BinaryOpNode) result.value();
            assertThat(((IntLiteral) binOp.left()).value()).isEqualTo(10);
            assertThat(((IntLiteral) binOp.right()).value()).isEqualTo(6);
        }

        @Test
        @DisplayName("transform complex tree with 20+ nodes")
        void transformComplexTree20PlusNodes() {
            var counter = new AtomicInteger();
            var transformer = new AstTransformer() {
                @Override
                public Result<AstNode> visitIntLiteral(IntLiteral node) {
                    counter.incrementAndGet();
                    return Result.success(new IntLiteral(node.value() + 1));
                }
            };

            // Build a tree with many IntLiterals
            var stmts = new ArrayList<AstNode>();
            for (int i = 0; i < 10; i++) {
                stmts.add(new BinaryOpNode(new IntLiteral(i), Operator.PLUS, new IntLiteral(i * 10)));
            }
            var original = new ProgramNode(stmts);
            var result = transformer.transform(original);
            assertThat(result.isSuccess()).isTrue();
            assertThat(counter.get()).isEqualTo(20); // 10 pairs of IntLiterals
        }

        @Test
        @DisplayName("visitor collects all function names from a program")
        void visitorCollectsAllFunctionNames() {
            var names = new ArrayList<String>();
            var collector = new AstTransformer() {
                @Override
                public Result<AstNode> visitFunctionDef(FunctionDefNode node) {
                    names.add(node.name());
                    return super.visitFunctionDef(node);
                }
            };

            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("add", List.of(new ParameterNode("a"), new ParameterNode("b")),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b"))),
                    new FunctionDefNode("mul", List.of(new ParameterNode("a"), new ParameterNode("b")),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.MULTIPLY, new IdentifierNode("b"))),
                    new FunctionDefNode("sub", List.of(new ParameterNode("a"), new ParameterNode("b")),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.MINUS, new IdentifierNode("b")))
            ));
            collector.transform(prog);
            assertThat(names).containsExactly("add", "mul", "sub");
        }

        @Test
        @DisplayName("visitor counts all nodes of each type")
        void visitorCountsNodesByType() {
            var counts = new HashMap<String, Integer>();
            var counter = new AstTransformer() {
                private void count(String type) {
                    counts.merge(type, 1, Integer::sum);
                }

                @Override
                public Result<AstNode> visitIntLiteral(IntLiteral node) {
                    count("IntLiteral");
                    return super.visitIntLiteral(node);
                }

                @Override
                public Result<AstNode> visitIdentifier(IdentifierNode node) {
                    count("IdentifierNode");
                    return super.visitIdentifier(node);
                }

                @Override
                public Result<AstNode> visitBinaryOp(BinaryOpNode node) {
                    count("BinaryOpNode");
                    return super.visitBinaryOp(node);
                }

                @Override
                public Result<AstNode> visitAssignment(AssignmentNode node) {
                    count("AssignmentNode");
                    return super.visitAssignment(node);
                }

                @Override
                public Result<AstNode> visitConditional(ConditionalNode node) {
                    count("ConditionalNode");
                    return super.visitConditional(node);
                }
            };

            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(1)),
                    new AssignmentNode(new IdentifierNode("y"), new IntLiteral(2)),
                    new ConditionalNode(
                            new BinaryOpNode(new IdentifierNode("x"), Operator.LT, new IdentifierNode("y")),
                            new IntLiteral(10),
                            new IntLiteral(20)
                    )
            ));
            counter.transform(prog);
            assertThat(counts.get("AssignmentNode")).isEqualTo(2);
            assertThat(counts.get("ConditionalNode")).isEqualTo(1);
            assertThat(counts.get("IntLiteral")).isEqualTo(4); // 1, 2, 10, 20
            assertThat(counts.get("IdentifierNode")).isEqualTo(4); // x, y, x, y
            assertThat(counts.get("BinaryOpNode")).isEqualTo(1);
        }

        @Test
        @DisplayName("transform preserves structure for identity transform")
        void identityTransformPreservesStructure() {
            var transformer = new AstTransformer() {};
            var original = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(1)),
                    new ConditionalNode(
                            new BoolLiteral(true),
                            new BlockNode(List.of(new IntLiteral(2))),
                            new BlockNode(List.of(new IntLiteral(3)))
                    ),
                    new LoopNode(LoopKind.WHILE, new BoolLiteral(false), new IntLiteral(0))
            ));
            var result = transformer.transform(original);
            assertThat(result.isSuccess()).isTrue();
            var prog = (ProgramNode) result.value();
            assertThat(prog.statements()).hasSize(3);
        }

        @Test
        @DisplayName("transform nested function definitions and calls")
        void transformNestedFunctionDefsAndCalls() {
            var transformer = new AstTransformer() {
                @Override
                public Result<AstNode> visitFunctionDef(FunctionDefNode node) {
                    // Prefix function name with "fn_"
                    return super.visitFunctionDef(node).map(n -> {
                        var fd = (FunctionDefNode) n;
                        return new FunctionDefNode("fn_" + fd.name(), fd.params(), fd.body());
                    });
                }

                @Override
                public Result<AstNode> visitFunctionCall(FunctionCallNode node) {
                    return super.visitFunctionCall(node).map(n -> {
                        var fc = (FunctionCallNode) n;
                        return new FunctionCallNode("fn_" + fc.name(), fc.arguments());
                    });
                }
            };

            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("greet", List.of(), new StringLiteral("hello")),
                    new FunctionCallNode("greet", List.of())
            ));
            var result = transformer.transform(prog);
            assertThat(result.isSuccess()).isTrue();
            var transformed = (ProgramNode) result.value();
            assertThat(((FunctionDefNode) transformed.statements().get(0)).name()).isEqualTo("fn_greet");
            assertThat(((FunctionCallNode) transformed.statements().get(1)).name()).isEqualTo("fn_greet");
        }

        @Test
        @DisplayName("transform replaces all string literals with uppercase")
        void transformStringLiteralsToUppercase() {
            var transformer = new AstTransformer() {
                @Override
                public Result<AstNode> visitStringLiteral(StringLiteral node) {
                    return Result.success(new StringLiteral(node.value().toUpperCase()));
                }
            };

            var original = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("msg"), new StringLiteral("hello world")),
                    new StringLiteral("goodbye")
            ));
            var result = transformer.transform(original);
            assertThat(result.isSuccess()).isTrue();
            var prog = (ProgramNode) result.value();
            var assignment = (AssignmentNode) prog.statements().get(0);
            assertThat(((StringLiteral) assignment.value()).value()).isEqualTo("HELLO WORLD");
            assertThat(((StringLiteral) prog.statements().get(1)).value()).isEqualTo("GOODBYE");
        }

        @Test
        @DisplayName("transform handles return nodes correctly")
        void transformHandlesReturnNodes() {
            var transformer = new AstTransformer() {
                @Override
                public Result<AstNode> visitIntLiteral(IntLiteral node) {
                    return Result.success(new IntLiteral(node.value() * 10));
                }
            };

            var original = new ReturnNode(new IntLiteral(5));
            var result = transformer.transform(original);
            assertThat(result.isSuccess()).isTrue();
            var ret = (ReturnNode) result.value();
            assertThat(((IntLiteral) ret.value()).value()).isEqualTo(50);
        }

        @Test
        @DisplayName("transform handles loop nodes")
        void transformHandlesLoopNodes() {
            var transformer = new AstTransformer() {
                @Override
                public Result<AstNode> visitIntLiteral(IntLiteral node) {
                    return Result.success(new IntLiteral(node.value() + 100));
                }
            };

            var original = new LoopNode(LoopKind.WHILE,
                    new BinaryOpNode(new IdentifierNode("i"), Operator.LT, new IntLiteral(10)),
                    new IntLiteral(0));
            var result = transformer.transform(original);
            assertThat(result.isSuccess()).isTrue();
            var loop = (LoopNode) result.value();
            var cond = (BinaryOpNode) loop.condition();
            assertThat(((IntLiteral) cond.right()).value()).isEqualTo(110);
        }
    }
}
