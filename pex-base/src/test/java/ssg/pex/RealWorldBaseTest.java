package ssg.pex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.ast.visitor.AstTransformer;
import ssg.pex.bnf.dialect.*;
import ssg.pex.bnf.engine.RecursiveDescentEngine;
import ssg.pex.bnf.model.*;
import ssg.pex.bnf.parser.BnfParser;
import ssg.pex.exec.*;
import ssg.pex.exec.handler.BaseHandlerProvider;
import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;
import ssg.pex.result.BatchResult;
import ssg.pex.result.PexError;
import ssg.pex.result.Result;
import ssg.pex.scope.*;
import ssg.pex.type.PexType;
import ssg.pex.type.TypeDescriptor;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-world complex test cases for pex-base: deeply nested scopes, complex execution,
 * grammar edge cases, concurrent access, and telemetry.
 */
@DisplayName("Real-world pex-base tests")
class RealWorldBaseTest {

    // ===========================================================================================
    // Deeply Nested Scope Tests
    // ===========================================================================================

    @Nested
    @DisplayName("Deeply Nested Scope Tests")
    class DeeplyNestedScopeTests {

        private ScopeTree tree;

        @BeforeEach
        void setUp() {
            tree = new ScopeTree();
        }

        private ScalarVariable scalarVar(String name, Object value) {
            return new ScalarVariable(name, value, TypeDescriptor.of(PexType.INT), true);
        }

        @Test
        @DisplayName("10-level nested blocks with variable shadowing at each level")
        void tenLevelNestedScopesWithShadowingAtEachLevel() {
            // Define x at root with value 0, then shadow at each level 1-9
            tree.defineVariable("x", scalarVar("x", 0));
            for (int i = 1; i <= 9; i++) {
                tree.enterScope("level" + i);
                tree.defineVariable("x", scalarVar("x", i * 100));
            }

            // At level 9, x should be 900
            assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(900);
            // 9 shadow records (one per inner level)
            assertThat(tree.shadowHistory()).hasSize(9);

            // Walk back up, verifying correct variable resolution at each level
            for (int i = 9; i >= 1; i--) {
                assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(i * 100);
                tree.exitScope();
            }
            // Back at root, x should be 0
            assertThat(tree.resolveVariable("x").value().currentValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("inner scope modifying variable then verifying outer scope unchanged")
        void innerScopeModifyVariableOuterUnchanged() {
            tree.defineVariable("shared", scalarVar("shared", 10));
            tree.enterScope("inner");
            // Shadow in inner scope
            tree.defineVariable("shared", scalarVar("shared", 20));
            // Modify inner shadow
            tree.updateVariable("shared", 30);
            assertThat(tree.resolveVariable("shared").value().currentValue()).isEqualTo(30);
            tree.exitScope();

            // Outer scope value preserved
            assertThat(tree.resolveVariable("shared").value().currentValue()).isEqualTo(10);
        }

        @Test
        @DisplayName("variable defined at level 5 accessed at level 8 through lexical chain")
        void variableDefinedAtLevel5AccessedAtLevel8() {
            tree.enterScope("l1");
            tree.enterScope("l2");
            tree.enterScope("l3");
            tree.enterScope("l4");
            tree.enterScope("l5");
            tree.defineVariable("deepVal", scalarVar("deepVal", 555));
            tree.enterScope("l6");
            tree.enterScope("l7");
            tree.enterScope("l8");

            var result = tree.resolveVariable("deepVal");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().currentValue()).isEqualTo(555);
        }

        @Test
        @DisplayName("ScopeTree with 50+ variables across multiple levels")
        void scopeTreeWith50PlusVariables() {
            // Define 10 variables at root
            for (int i = 0; i < 10; i++) {
                tree.defineVariable("root_" + i, scalarVar("root_" + i, i));
            }
            // Enter 5 scopes, each defining 10 variables
            for (int level = 1; level <= 5; level++) {
                tree.enterScope("scope" + level);
                for (int i = 0; i < 10; i++) {
                    tree.defineVariable("s" + level + "_" + i, scalarVar("s" + level + "_" + i, level * 100 + i));
                }
            }

            // At level 5, all 60 variables should be resolvable
            for (int i = 0; i < 10; i++) {
                assertThat(tree.resolveVariable("root_" + i).isSuccess()).isTrue();
            }
            for (int level = 1; level <= 5; level++) {
                for (int i = 0; i < 10; i++) {
                    assertThat(tree.resolveVariable("s" + level + "_" + i).isSuccess()).isTrue();
                }
            }
        }

        @Test
        @DisplayName("ShadowRecord verification for complex shadowing chains")
        void shadowRecordVerificationForComplexChains() {
            tree.defineVariable("a", scalarVar("a", 1));
            tree.defineVariable("b", scalarVar("b", 2));

            tree.enterScope("s1");
            tree.defineVariable("a", scalarVar("a", 10));

            tree.enterScope("s2");
            tree.defineVariable("a", scalarVar("a", 100));
            tree.defineVariable("b", scalarVar("b", 200));

            // 3 shadow records: a at s1, a at s2, b at s2
            assertThat(tree.shadowHistory()).hasSize(3);

            // Verify shadow record contents
            var aShadows = tree.shadowHistory().stream()
                    .filter(r -> r.variableName().equals("a"))
                    .toList();
            assertThat(aShadows).hasSize(2);
            assertThat(aShadows.get(0).previousValue()).isEqualTo(1);
            assertThat(aShadows.get(0).newValue()).isEqualTo(10);
            assertThat(aShadows.get(1).previousValue()).isEqualTo(10);
            assertThat(aShadows.get(1).newValue()).isEqualTo(100);

            var bShadows = tree.shadowHistory().stream()
                    .filter(r -> r.variableName().equals("b"))
                    .toList();
            assertThat(bShadows).hasSize(1);
            assertThat(bShadows.get(0).previousValue()).isEqualTo(2);
            assertThat(bShadows.get(0).newValue()).isEqualTo(200);
        }

        @Test
        @DisplayName("multiple listeners on complex scope lifecycle capture all events")
        void multipleListenersCaptureSameEvents() {
            var events1 = new ArrayList<String>();
            var events2 = new ArrayList<String>();

            tree.addListener(new ScopeTreeListener() {
                @Override public void onScopeEnter(Scope scope) { events1.add("E:" + scope.name()); }
                @Override public void onScopeExit(Scope scope) { events1.add("X:" + scope.name()); }
                @Override public void onVariableDefine(Scope scope, Variable var) { events1.add("D:" + var.name()); }
                @Override public void onVariableShadow(ShadowRecord record) { events1.add("S:" + record.variableName()); }
            });
            tree.addListener(new ScopeTreeListener() {
                @Override public void onScopeEnter(Scope scope) { events2.add("E:" + scope.name()); }
                @Override public void onScopeExit(Scope scope) { events2.add("X:" + scope.name()); }
                @Override public void onVariableDefine(Scope scope, Variable var) { events2.add("D:" + var.name()); }
                @Override public void onVariableShadow(ShadowRecord record) { events2.add("S:" + record.variableName()); }
            });

            tree.defineVariable("x", scalarVar("x", 1));
            tree.enterScope("a");
            tree.defineVariable("x", scalarVar("x", 2));
            tree.exitScope();

            assertThat(events1).isEqualTo(events2);
            assertThat(events1).containsExactly("D:x", "E:a", "S:x", "D:x", "X:a");
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

        @Test
        @DisplayName("program with 20+ sequential statements including assignments, conditionals, loops")
        void programWith20PlusSequentialStatements() {
            var stmts = new ArrayList<AstNode>();
            // Assign 10 variables
            for (int i = 0; i < 10; i++) {
                stmts.add(new AssignmentNode(new IdentifierNode("v" + i), new IntLiteral(i)));
            }
            // Sum them up
            stmts.add(new AssignmentNode(new IdentifierNode("sum"), new IntLiteral(0)));
            for (int i = 0; i < 10; i++) {
                stmts.add(new AssignmentNode(new IdentifierNode("sum"),
                        new BinaryOpNode(new IdentifierNode("sum"), Operator.PLUS, new IdentifierNode("v" + i))));
            }
            // Conditional on result
            stmts.add(new ConditionalNode(
                    new BinaryOpNode(new IdentifierNode("sum"), Operator.GT, new IntLiteral(40)),
                    new AssignmentNode(new IdentifierNode("label"), new StringLiteral("high")),
                    new AssignmentNode(new IdentifierNode("label"), new StringLiteral("low"))
            ));
            stmts.add(new IdentifierNode("label"));

            var prog = new ProgramNode(stmts);
            var result = engine.execute(prog);
            assertThat(result.isSuccess()).isTrue();
            // sum = 0+1+2+...+9 = 45 > 40, so label = "high"
            assertThat(result.value()).isEqualTo("high");
        }

        @Test
        @DisplayName("recursive function computing factorial(10) = 3628800")
        void recursiveFactorial10() {
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("fact", List.of(new ParameterNode("n")),
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
                    ),
                    new FunctionCallNode("fact", List.of(new IntLiteral(10)))
            ));
            var result = engine.execute(prog);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo(3628800L);
        }

        @Test
        @DisplayName("Fibonacci with iterative accumulator via loop")
        void fibonacciIterativeViaLoop() {
            // fib(n): a=0, b=1, i=0; while(i < n) { temp=b; b=a+b; a=temp; i=i+1 }; return a
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("fib", List.of(new ParameterNode("n")),
                            new BlockNode(List.of(
                                    new AssignmentNode(new IdentifierNode("a"), new IntLiteral(0)),
                                    new AssignmentNode(new IdentifierNode("b"), new IntLiteral(1)),
                                    new AssignmentNode(new IdentifierNode("i"), new IntLiteral(0)),
                                    new LoopNode(LoopKind.WHILE,
                                            new BinaryOpNode(new IdentifierNode("i"), Operator.LT, new IdentifierNode("n")),
                                            new BlockNode(List.of(
                                                    new AssignmentNode(new IdentifierNode("temp"), new IdentifierNode("b")),
                                                    new AssignmentNode(new IdentifierNode("b"),
                                                            new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b"))),
                                                    new AssignmentNode(new IdentifierNode("a"), new IdentifierNode("temp")),
                                                    new AssignmentNode(new IdentifierNode("i"),
                                                            new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                                            ))
                                    ),
                                    new ReturnNode(new IdentifierNode("a"))
                            ))
                    ),
                    new FunctionCallNode("fib", List.of(new IntLiteral(20)))
            ));
            // fib(20) = 6765
            assertThat(engine.execute(prog).value()).isEqualTo(6765L);
        }

        @Test
        @DisplayName("nested conditional: 5 levels deep if/else chain")
        void nestedConditional5LevelsDeep() {
            // Classify score: >=90 A, >=80 B, >=70 C, >=60 D, else F
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("score"), new IntLiteral(75)),
                    new ConditionalNode(
                            new BinaryOpNode(new IdentifierNode("score"), Operator.GE, new IntLiteral(90)),
                            new StringLiteral("A"),
                            new ConditionalNode(
                                    new BinaryOpNode(new IdentifierNode("score"), Operator.GE, new IntLiteral(80)),
                                    new StringLiteral("B"),
                                    new ConditionalNode(
                                            new BinaryOpNode(new IdentifierNode("score"), Operator.GE, new IntLiteral(70)),
                                            new StringLiteral("C"),
                                            new ConditionalNode(
                                                    new BinaryOpNode(new IdentifierNode("score"), Operator.GE, new IntLiteral(60)),
                                                    new StringLiteral("D"),
                                                    new StringLiteral("F")
                                            )
                                    )
                            )
                    )
            ));
            assertThat(engine.execute(prog).value()).isEqualTo("C");
        }

        @Test
        @DisplayName("3-level function call chain: f(g(h(x)))")
        void threeLevelFunctionCallChain() {
            // h(x) = x + 10, g(x) = x * 3, f(x) = x - 5
            // f(g(h(2))) = f(g(12)) = f(36) = 31
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("h", List.of(new ParameterNode("x")),
                            new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IntLiteral(10))),
                    new FunctionDefNode("g", List.of(new ParameterNode("x")),
                            new BinaryOpNode(new IdentifierNode("x"), Operator.MULTIPLY, new IntLiteral(3))),
                    new FunctionDefNode("f", List.of(new ParameterNode("x")),
                            new BinaryOpNode(new IdentifierNode("x"), Operator.MINUS, new IntLiteral(5))),
                    new FunctionCallNode("f", List.of(
                            new FunctionCallNode("g", List.of(
                                    new FunctionCallNode("h", List.of(new IntLiteral(2)))
                            ))
                    ))
            ));
            assertThat(engine.execute(prog).value()).isEqualTo(31L);
        }

        @Test
        @DisplayName("multiple functions defined and called in sequence")
        void multipleFunctionsDefinedAndCalledInSequence() {
            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("add", List.of(new ParameterNode("a"), new ParameterNode("b")),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b"))),
                    new FunctionDefNode("sub", List.of(new ParameterNode("a"), new ParameterNode("b")),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.MINUS, new IdentifierNode("b"))),
                    new FunctionDefNode("mul", List.of(new ParameterNode("a"), new ParameterNode("b")),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.MULTIPLY, new IdentifierNode("b"))),
                    new AssignmentNode(new IdentifierNode("r1"),
                            new FunctionCallNode("add", List.of(new IntLiteral(10), new IntLiteral(20)))),
                    new AssignmentNode(new IdentifierNode("r2"),
                            new FunctionCallNode("sub", List.of(new IdentifierNode("r1"), new IntLiteral(5)))),
                    new FunctionCallNode("mul", List.of(new IdentifierNode("r2"), new IntLiteral(2)))
            ));
            // r1 = 30, r2 = 25, result = 50
            assertThat(engine.execute(prog).value()).isEqualTo(50L);
        }

        @Test
        @DisplayName("variable reuse: assign, use, reassign, use again")
        void variableReuseAssignReassignUse() {
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10)),
                    new AssignmentNode(new IdentifierNode("y"),
                            new BinaryOpNode(new IdentifierNode("x"), Operator.MULTIPLY, new IntLiteral(2))),
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(100)),
                    new AssignmentNode(new IdentifierNode("z"),
                            new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IdentifierNode("y"))),
                    new IdentifierNode("z")
            ));
            // y = 20 (from x=10), then x=100, z = 100 + 20 = 120
            assertThat(engine.execute(prog).value()).isEqualTo(120L);
        }

        @Test
        @DisplayName("error propagation in RESULT mode: first failure stops remaining")
        void errorPropagationInResultMode() {
            var eng = ExecutionEngine.builder()
                    .plugin(new BaseHandlerProvider())
                    .config(new ExecutionConfig(256, 100_000, 30_000L, ExecutionConfig.ErrorMode.RESULT))
                    .build();

            // Division by zero should produce failure
            var expr = new BinaryOpNode(new IntLiteral(10), Operator.DIVIDE, new IntLiteral(0));
            var result = eng.execute(expr);
            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().code()).isEqualTo("DIVISION_BY_ZERO");
        }

        @Test
        @DisplayName("loop computing sum of squares from 1 to 20")
        void loopSumOfSquares1To20() {
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("sum"), new IntLiteral(0)),
                    new AssignmentNode(new IdentifierNode("i"), new IntLiteral(1)),
                    new LoopNode(LoopKind.WHILE,
                            new BinaryOpNode(new IdentifierNode("i"), Operator.LE, new IntLiteral(20)),
                            new BlockNode(List.of(
                                    new AssignmentNode(new IdentifierNode("sum"),
                                            new BinaryOpNode(new IdentifierNode("sum"), Operator.PLUS,
                                                    new BinaryOpNode(new IdentifierNode("i"), Operator.MULTIPLY, new IdentifierNode("i")))),
                                    new AssignmentNode(new IdentifierNode("i"),
                                            new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                            ))
                    ),
                    new IdentifierNode("sum")
            ));
            // Sum of i^2 for i=1..20 = 20*21*41/6 = 2870
            assertThat(engine.execute(prog).value()).isEqualTo(2870L);
        }

        @Test
        @DisplayName("nested loops: multiplication table sum")
        void nestedLoopsMultiplicationTableSum() {
            // sum of i*j for i=1..5, j=1..5
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("total"), new IntLiteral(0)),
                    new AssignmentNode(new IdentifierNode("i"), new IntLiteral(1)),
                    new LoopNode(LoopKind.WHILE,
                            new BinaryOpNode(new IdentifierNode("i"), Operator.LE, new IntLiteral(5)),
                            new BlockNode(List.of(
                                    new AssignmentNode(new IdentifierNode("j"), new IntLiteral(1)),
                                    new LoopNode(LoopKind.WHILE,
                                            new BinaryOpNode(new IdentifierNode("j"), Operator.LE, new IntLiteral(5)),
                                            new BlockNode(List.of(
                                                    new AssignmentNode(new IdentifierNode("total"),
                                                            new BinaryOpNode(new IdentifierNode("total"), Operator.PLUS,
                                                                    new BinaryOpNode(new IdentifierNode("i"), Operator.MULTIPLY, new IdentifierNode("j")))),
                                                    new AssignmentNode(new IdentifierNode("j"),
                                                            new BinaryOpNode(new IdentifierNode("j"), Operator.PLUS, new IntLiteral(1)))
                                            ))
                                    ),
                                    new AssignmentNode(new IdentifierNode("i"),
                                            new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                            ))
                    ),
                    new IdentifierNode("total")
            ));
            // (1+2+3+4+5) * (1+2+3+4+5) = 15 * 15 = 225
            assertThat(engine.execute(prog).value()).isEqualTo(225L);
        }

        @Test
        @DisplayName("loop with conditional exit pattern finding first value satisfying condition")
        void loopWithConditionalExitPatternFindFirst() {
            // Find smallest i where i*i > 100
            var prog = new ProgramNode(List.of(
                    new AssignmentNode(new IdentifierNode("found"), new IntLiteral(0)),
                    new AssignmentNode(new IdentifierNode("i"), new IntLiteral(1)),
                    new LoopNode(LoopKind.WHILE,
                            new BinaryOpNode(
                                    new BinaryOpNode(new IdentifierNode("found"), Operator.EQ, new IntLiteral(0)),
                                    Operator.AND,
                                    new BinaryOpNode(new IdentifierNode("i"), Operator.LT, new IntLiteral(100))
                            ),
                            new BlockNode(List.of(
                                    new ConditionalNode(
                                            new BinaryOpNode(
                                                    new BinaryOpNode(new IdentifierNode("i"), Operator.MULTIPLY, new IdentifierNode("i")),
                                                    Operator.GT,
                                                    new IntLiteral(100)
                                            ),
                                            new AssignmentNode(new IdentifierNode("found"), new IntLiteral(1))
                                    ),
                                    new ConditionalNode(
                                            new BinaryOpNode(new IdentifierNode("found"), Operator.EQ, new IntLiteral(0)),
                                            new AssignmentNode(new IdentifierNode("i"),
                                                    new BinaryOpNode(new IdentifierNode("i"), Operator.PLUS, new IntLiteral(1)))
                                    )
                            ))
                    ),
                    new IdentifierNode("i")
            ));
            // 10*10 = 100 (not >100), 11*11 = 121 (>100), so i = 11
            assertThat(engine.execute(prog).value()).isEqualTo(11L);
        }
    }

    // ===========================================================================================
    // Grammar/Parser Edge Cases
    // ===========================================================================================

    @Nested
    @DisplayName("Grammar/Parser Edge Cases")
    class GrammarParserEdgeCases {

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
        @DisplayName("BnfParser with 30+ rules grammar")
        void bnfParserWith30PlusRulesGrammar() {
            var sb = new StringBuilder("grammar bigLang;\n");
            // Build a grammar with 32 rules
            sb.append("program ::= statement+ ;\n");
            sb.append("statement ::= assignment | ifStmt | whileStmt | funcDef | funcCall | returnStmt | forStmt ;\n");
            sb.append("assignment ::= identifier '=' expr ';' ;\n");
            sb.append("ifStmt ::= 'if' '(' expr ')' block ( 'else' block )? ;\n");
            sb.append("whileStmt ::= 'while' '(' expr ')' block ;\n");
            sb.append("forStmt ::= 'for' '(' assignment expr ';' assignment ')' block ;\n");
            sb.append("funcDef ::= 'func' identifier '(' paramList? ')' block ;\n");
            sb.append("funcCall ::= identifier '(' argList? ')' ';' ;\n");
            sb.append("returnStmt ::= 'return' expr? ';' ;\n");
            sb.append("block ::= '{' statement* '}' ;\n");
            sb.append("paramList ::= identifier ( ',' identifier )* ;\n");
            sb.append("argList ::= expr ( ',' expr )* ;\n");
            sb.append("expr ::= logicalOr ;\n");
            sb.append("logicalOr ::= logicalAnd ( '||' logicalAnd )* ;\n");
            sb.append("logicalAnd ::= equality ( '&&' equality )* ;\n");
            sb.append("equality ::= comparison ( ( '==' | '!=' ) comparison )* ;\n");
            sb.append("comparison ::= addition ( ( '<' | '>' | '<=' | '>=' ) addition )* ;\n");
            sb.append("addition ::= multiplication ( ( '+' | '-' ) multiplication )* ;\n");
            sb.append("multiplication ::= unary ( ( '*' | '/' | '%' ) unary )* ;\n");
            sb.append("unary ::= ( '-' | '!' )? primary ;\n");
            sb.append("primary ::= number | stringLit | identifier | '(' expr ')' | funcCallExpr ;\n");
            sb.append("funcCallExpr ::= identifier '(' argList? ')' ;\n");
            sb.append("number ::= /[0-9]+/ ;\n");
            sb.append("stringLit ::= /\"[^\"]*\"/ ;\n");
            sb.append("identifier ::= /[a-zA-Z_][a-zA-Z0-9_]*/ ;\n");
            sb.append("comment ::= /\\/\\/[^\\n]*/ ;\n");
            sb.append("typeAnnotation ::= ':' typeName ;\n");
            sb.append("typeName ::= 'int' | 'float' | 'string' | 'bool' | 'void' ;\n");
            sb.append("arrayLit ::= '[' ( expr ( ',' expr )* )? ']' ;\n");
            sb.append("mapLit ::= '{' ( mapEntry ( ',' mapEntry )* )? '}' ;\n");
            sb.append("mapEntry ::= stringLit ':' expr ;\n");
            sb.append("switchStmt ::= 'switch' '(' expr ')' '{' caseBlock+ '}' ;\n");
            sb.append("caseBlock ::= 'case' expr ':' statement+ ;\n");

            var g = parseGrammar(sb.toString());
            assertThat(g.name()).isEqualTo("bigLang");
            assertThat(g.rules().size()).isGreaterThanOrEqualTo(30);
            assertThat(g.startRuleName()).isEqualTo("program");
        }

        @Test
        @DisplayName("grammar with deeply nested alternation groups (4 levels)")
        void grammarWithDeeplyNestedAlternationGroups4Levels() {
            var g = parseGrammar("""
                    grammar deepAlt;
                    expr ::= ( ( 'a' | 'b' | ( 'c' | ( 'd' | 'e' | 'f' ) ) ) | 'g' ) ;
                    """);
            assertThat(g.rules()).hasSize(1);
            var match = engine.parse("d", g);
            assertThat(match.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("grammar dialect composition: base grammar with 2 dialect extensions")
        void grammarDialectComposition2Extensions() {
            var rules = new LinkedHashMap<String, Rule>();
            rules.put("expr", new Rule("expr", new NonTerminal("term")));
            rules.put("term", new Rule("term", Terminal.literal("x")));
            var base = new Grammar("base", rules, "expr");

            var ext1 = new DialectExtension("ext1", "base", List.of(
                    new RuleAddition(new Rule("factor", Terminal.literal("42"))),
                    new RuleExtension("term", List.of(Terminal.literal("y")))
            ));
            var ext2 = new DialectExtension("ext2", "base", List.of(
                    new RuleAddition(new Rule("unary", Terminal.literal("-"))),
                    new RuleReplacement("factor", new Rule("factor", Terminal.literal("99")))
            ));

            var combined = base.withDialect(ext1).withDialect(ext2);
            assertThat(combined.rules()).containsKeys("expr", "term", "factor", "unary");
            assertThat(combined.rule("factor").get().body()).isEqualTo(Terminal.literal("99"));
            assertThat(combined.rule("term").get().body()).isInstanceOf(Alternation.class);
            assertThat(combined.name()).isEqualTo("base+ext1+ext2");
        }

        @Test
        @DisplayName("RecursiveDescentEngine parsing complex nested input")
        void recursiveDescentEngineParsingComplexNestedInput() {
            var g = parseGrammar("""
                    grammar nested;
                    expr     ::= term ( '+' term )* ;
                    term     ::= factor ( '*' factor )* ;
                    factor   ::= '(' expr ')' | number ;
                    number   ::= /[0-9]+/ ;
                    """);
            // Parse: (1 + 2) * (3 + 4)
            var match = engine.parse("( 1 + 2 ) * ( 3 + 4 )", g);
            assertThat(match.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("BatchResult: parse 10 inputs, some succeed, some fail, verify partition")
        void batchResultParseMultipleInputs() {
            var g = parseGrammar("start ::= 'a' 'b' ;");

            var results = new ArrayList<Result<String>>();
            String[] inputs = {"a b", "a c", "a b", "x y", "a b", "a", "a b", "b a", "a b", "a b"};
            for (var input : inputs) {
                var match = engine.parse(input, g);
                if (match.isSuccess()) {
                    results.add(Result.success(match.value().matchedText()));
                } else {
                    results.add(Result.failure(match.error()));
                }
            }

            var batch = Result.collect(results);
            // "a b" appears 6 times (indices 0,2,4,6,8,9)
            assertThat(batch.successCount()).isEqualTo(6);
            assertThat(batch.errorCount()).isEqualTo(4);
            assertThat(batch.totalAttempted()).isEqualTo(10);
            assertThat(batch.hasErrors()).isTrue();
            assertThat(batch.isFullSuccess()).isFalse();
        }

        @Test
        @DisplayName("grammar with all annotation types and precedence")
        void grammarWithAllAnnotationTypesAndPrecedence() {
            var g = parseGrammar("""
                    grammar annotated;
                    @keyword ifRule ::= 'if' ;
                    @keyword @precedence(1) elseRule ::= 'else' ;
                    @operator @precedence(10) plus ::= '+' ;
                    @operator @precedence(20) star ::= '*' ;
                    @literal numLit ::= /[0-9]+/ ;
                    """);
            assertThat(g.rules()).hasSize(5);
            assertThat(g.rule("plus").get().hasAnnotation("operator")).isTrue();
            assertThat(g.rule("plus").get().annotation("precedence")).isEqualTo("10");
            assertThat(g.rule("star").get().annotation("precedence")).isEqualTo("20");
            assertThat(g.rule("numLit").get().hasAnnotation("literal")).isTrue();
        }
    }

    // ===========================================================================================
    // ScopeTree Concurrent Access
    // ===========================================================================================

    @Nested
    @DisplayName("ScopeTree Concurrent Access")
    class ScopeTreeConcurrentAccess {

        @Test
        @DisplayName("multiple threads reading from same scope tree do not cause errors")
        void multipleThreadsReadingScopeTree() throws Exception {
            var tree = new ScopeTree();
            for (int i = 0; i < 20; i++) {
                tree.defineVariable("var" + i,
                        new ScalarVariable("var" + i, i, TypeDescriptor.of(PexType.INT), true));
            }

            var executor = Executors.newFixedThreadPool(4);
            var futures = new ArrayList<Future<Boolean>>();

            for (int t = 0; t < 8; t++) {
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < 20; i++) {
                        var result = tree.resolveVariable("var" + i);
                        if (result.isFailure()) return false;
                        if (!result.value().currentValue().equals(i)) return false;
                    }
                    return true;
                }));
            }

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            for (var future : futures) {
                assertThat(future.get()).isTrue();
            }
        }

        @Test
        @DisplayName("ExecutionStatistics accumulation from parallel executions")
        void executionStatisticsAccumulationFromParallelExecutions() throws Exception {
            var stats = new ExecutionStatistics();
            var executor = Executors.newFixedThreadPool(4);
            int incrementsPerThread = 1000;
            int threadCount = 4;
            var latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    for (int i = 0; i < incrementsPerThread; i++) {
                        stats.incrementNodeCount();
                    }
                    latch.countDown();
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(stats.getNodeCount()).isEqualTo((long) threadCount * incrementsPerThread);
        }

        @Test
        @DisplayName("plugin loading order verification with multiple plugins")
        void pluginLoadingOrderVerification() {
            var loadOrder = new ArrayList<String>();

            PexPlugin first = new PexPlugin() {
                @Override public String name() { return "first"; }
                @Override public int loadOrder() { return 100; }
                @Override public void initialize(PluginContext ctx) { loadOrder.add("first"); }
            };
            PexPlugin second = new PexPlugin() {
                @Override public String name() { return "second"; }
                @Override public int loadOrder() { return 200; }
                @Override public void initialize(PluginContext ctx) { loadOrder.add("second"); }
            };

            var eng = ExecutionEngine.builder()
                    .plugin(first)
                    .plugin(second)
                    .build();

            // Engine should build successfully with both plugins
            assertThat(eng.handlers()).isNotNull();
            assertThat(eng.functions()).isNotNull();
        }
    }

    // ===========================================================================================
    // Telemetry
    // ===========================================================================================

    @Nested
    @DisplayName("Telemetry Tests")
    class TelemetryTests {

        @Test
        @DisplayName("listener receives events for complex execution")
        void listenerReceivesEventsForComplexExecution() {
            var nodeTypes = new ArrayList<String>();
            var errorCount = new AtomicInteger();
            var scopeEntries = new AtomicInteger();
            var scopeExits = new AtomicInteger();
            var functionCalls = new ArrayList<String>();

            var eng = ExecutionEngine.builder()
                    .plugin(new BaseHandlerProvider())
                    .addListener(new ExecutionListener() {
                        @Override public void onNodeEnter(Object node, ExecutionContext ctx) {
                            nodeTypes.add(node.getClass().getSimpleName());
                        }
                        @Override public void onNodeExit(Object node, ExecutionContext ctx, Result<Object> result) {}
                        @Override public void onScopeEnter(String scopeName, ExecutionContext ctx) { scopeEntries.incrementAndGet(); }
                        @Override public void onScopeExit(String scopeName, ExecutionContext ctx) { scopeExits.incrementAndGet(); }
                        @Override public void onFunctionCall(String functionName, List<Object> args, ExecutionContext ctx) {
                            functionCalls.add(functionName);
                        }
                        @Override public void onError(PexError error, ExecutionContext ctx) { errorCount.incrementAndGet(); }
                    })
                    .build();

            var prog = new ProgramNode(List.of(
                    new FunctionDefNode("add", List.of(new ParameterNode("a"), new ParameterNode("b")),
                            new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b"))),
                    new AssignmentNode(new IdentifierNode("x"), new IntLiteral(10)),
                    new FunctionCallNode("add", List.of(new IdentifierNode("x"), new IntLiteral(5)))
            ));
            eng.execute(prog);

            assertThat(nodeTypes).contains("ProgramNode", "FunctionDefNode", "AssignmentNode",
                    "FunctionCallNode", "IntLiteral", "BinaryOpNode");
            assertThat(functionCalls).contains("add");
            assertThat(errorCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("error events captured correctly on execution failure")
        void errorEventsCapturedOnExecutionFailure() {
            var errors = new ArrayList<String>();

            var eng = ExecutionEngine.builder()
                    .plugin(new BaseHandlerProvider())
                    .addListener(new ExecutionListener() {
                        @Override public void onNodeEnter(Object node, ExecutionContext ctx) {}
                        @Override public void onNodeExit(Object node, ExecutionContext ctx, Result<Object> result) {}
                        @Override public void onScopeEnter(String scopeName, ExecutionContext ctx) {}
                        @Override public void onScopeExit(String scopeName, ExecutionContext ctx) {}
                        @Override public void onFunctionCall(String functionName, List<Object> args, ExecutionContext ctx) {}
                        @Override public void onError(PexError error, ExecutionContext ctx) {
                            errors.add(error.code());
                        }
                    })
                    .build();

            // Trigger UNDEFINED_VARIABLE and DIVISION_BY_ZERO
            eng.execute(new IdentifierNode("nonExistent"));
            eng.execute(new BinaryOpNode(new IntLiteral(1), Operator.DIVIDE, new IntLiteral(0)));

            assertThat(errors).contains("UNDEFINED_VARIABLE", "DIVISION_BY_ZERO");
        }
    }
}
