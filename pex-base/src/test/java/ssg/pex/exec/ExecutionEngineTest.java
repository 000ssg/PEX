package ssg.pex.exec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.ast.node.*;
import ssg.pex.exec.handler.BaseHandlerProvider;
import ssg.pex.result.PexError;
import ssg.pex.result.Result;
import ssg.pex.scope.ScalarVariable;
import ssg.pex.type.PexType;
import ssg.pex.type.TypeDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionEngineTest {

    private ExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .build();
    }

    // --- HandlerRegistry ---

    @Test
    void handlerRegistry_registerAndGet() {
        var registry = new HandlerRegistry();
        NodeHandler handler = (node, ctx) -> Result.success("handled");
        registry.register(String.class, handler);
        assertThat(registry.getHandler(String.class)).isSameAs(handler);
    }

    @Test
    void handlerRegistry_getHandler_nullForUnregistered() {
        var registry = new HandlerRegistry();
        assertThat(registry.getHandler(Integer.class)).isNull();
    }

    @Test
    void handlerRegistry_hierarchyBasedLookup() {
        var registry = new HandlerRegistry();
        NodeHandler handler = (node, ctx) -> Result.success("via interface");
        registry.register(LiteralNode.class, handler);
        // IntLiteral implements LiteralNode (via interface chain)
        assertThat(registry.getHandler(IntLiteral.class)).isSameAs(handler);
    }

    @Test
    void handlerRegistry_hasHandler() {
        var registry = new HandlerRegistry();
        registry.register(String.class, (n, c) -> Result.success(null));
        assertThat(registry.hasHandler(String.class)).isTrue();
        assertThat(registry.hasHandler(Integer.class)).isFalse();
    }

    // --- FunctionRegistry ---

    @Test
    void functionRegistry_registerAndLookup() {
        var reg = new FunctionRegistry();
        var def = new FunctionDef("add", List.of("a", "b"), null, null);
        reg.register("add", def);
        assertThat(reg.lookup("add")).isPresent();
        assertThat(reg.lookup("add").get().name()).isEqualTo("add");
    }

    @Test
    void functionRegistry_registerNative() {
        var reg = new FunctionRegistry();
        reg.registerNative("print", List.of("msg"), (args, ctx) -> Result.success(null));
        assertThat(reg.hasFunction("print")).isTrue();
        assertThat(reg.lookup("print").get().isNative()).isTrue();
    }

    @Test
    void functionRegistry_lookupMissing() {
        var reg = new FunctionRegistry();
        assertThat(reg.lookup("ghost")).isEmpty();
        assertThat(reg.hasFunction("ghost")).isFalse();
    }

    // --- ExecutionConfig ---

    @Test
    void executionConfig_defaults() {
        var cfg = ExecutionConfig.defaults();
        assertThat(cfg.maxRecursionDepth()).isEqualTo(256);
        assertThat(cfg.maxLoopIterations()).isEqualTo(100_000);
        assertThat(cfg.timeoutMillis()).isEqualTo(30_000L);
        assertThat(cfg.errorMode()).isEqualTo(ExecutionConfig.ErrorMode.RESULT);
    }

    // --- ExecutionStatistics ---

    @Test
    void executionStatistics_incrementAndGet() {
        var stats = new ExecutionStatistics();
        stats.incrementNodeCount();
        stats.incrementNodeCount();
        stats.incrementFunctionCalls();
        stats.incrementScopeEntries();
        stats.incrementScopeExits();
        stats.incrementErrors();

        assertThat(stats.getNodeCount()).isEqualTo(2);
        assertThat(stats.getFunctionCalls()).isEqualTo(1);
        assertThat(stats.getScopeEntries()).isEqualTo(1);
        assertThat(stats.getScopeExits()).isEqualTo(1);
        assertThat(stats.getErrors()).isEqualTo(1);
    }

    @Test
    void executionStatistics_snapshot() {
        var stats = new ExecutionStatistics();
        stats.incrementNodeCount();
        var snap = stats.snapshot();
        assertThat(snap).containsEntry("nodeCount", 1L);
        assertThat(snap).containsEntry("errors", 0L);
    }

    @Test
    void executionStatistics_reset() {
        var stats = new ExecutionStatistics();
        stats.incrementNodeCount();
        stats.incrementErrors();
        stats.reset();
        assertThat(stats.getNodeCount()).isZero();
        assertThat(stats.getErrors()).isZero();
    }

    // --- Execute literal nodes ---

    @Test
    void execute_intLiteral() {
        var result = engine.execute(new IntLiteral(42));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(42L);
    }

    @Test
    void execute_floatLiteral() {
        var result = engine.execute(new FloatLiteral(3.14));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(3.14);
    }

    @Test
    void execute_stringLiteral() {
        var result = engine.execute(new StringLiteral("hello"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo("hello");
    }

    @Test
    void execute_boolLiteral() {
        var result = engine.execute(new BoolLiteral(true));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(true);
    }

    @Test
    void execute_nullLiteral() {
        var result = engine.execute(new NullLiteral());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isNull();
    }

    // --- Execute identifier ---

    @Test
    void execute_identifier_resolvedFromScope() {
        // Use a program that assigns then reads
        var program = new ProgramNode(List.of(
                new AssignmentNode(new IdentifierNode("x"), new IntLiteral(99)),
                new IdentifierNode("x")
        ));
        var result = engine.execute(program);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(99L);
    }

    @Test
    void execute_identifier_undefinedFails() {
        var result = engine.execute(new IdentifierNode("undefined"));
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("UNDEFINED_VARIABLE");
    }

    // --- Execute binary operations ---

    @Test
    void execute_binaryOp_addition() {
        var node = new BinaryOpNode(new IntLiteral(3), Operator.PLUS, new IntLiteral(4));
        var result = engine.execute(node);
        assertThat(result.value()).isEqualTo(7L);
    }

    @Test
    void execute_binaryOp_subtraction() {
        var node = new BinaryOpNode(new IntLiteral(10), Operator.MINUS, new IntLiteral(3));
        assertThat(engine.execute(node).value()).isEqualTo(7L);
    }

    @Test
    void execute_binaryOp_multiplication() {
        var node = new BinaryOpNode(new IntLiteral(5), Operator.MULTIPLY, new IntLiteral(6));
        assertThat(engine.execute(node).value()).isEqualTo(30L);
    }

    @Test
    void execute_binaryOp_division() {
        var node = new BinaryOpNode(new IntLiteral(10), Operator.DIVIDE, new IntLiteral(3));
        assertThat(engine.execute(node).value()).isEqualTo(3L); // long division
    }

    @Test
    void execute_binaryOp_modulo() {
        var node = new BinaryOpNode(new IntLiteral(10), Operator.MODULO, new IntLiteral(3));
        assertThat(engine.execute(node).value()).isEqualTo(1L);
    }

    @Test
    void execute_binaryOp_comparison_lt() {
        var node = new BinaryOpNode(new IntLiteral(3), Operator.LT, new IntLiteral(5));
        assertThat(engine.execute(node).value()).isEqualTo(true);
    }

    @Test
    void execute_binaryOp_comparison_eq() {
        var node = new BinaryOpNode(new IntLiteral(5), Operator.EQ, new IntLiteral(5));
        assertThat(engine.execute(node).value()).isEqualTo(true);
    }

    @Test
    void execute_binaryOp_comparison_neq() {
        var node = new BinaryOpNode(new IntLiteral(5), Operator.NEQ, new IntLiteral(3));
        assertThat(engine.execute(node).value()).isEqualTo(true);
    }

    @Test
    void execute_binaryOp_logicalAnd_shortCircuit() {
        var node = new BinaryOpNode(new BoolLiteral(false), Operator.AND, new BoolLiteral(true));
        assertThat(engine.execute(node).value()).isEqualTo(false);
    }

    @Test
    void execute_binaryOp_logicalOr_shortCircuit() {
        var node = new BinaryOpNode(new BoolLiteral(true), Operator.OR, new BoolLiteral(false));
        assertThat(engine.execute(node).value()).isEqualTo(true);
    }

    @Test
    void execute_binaryOp_stringConcat() {
        var node = new BinaryOpNode(new StringLiteral("hello "), Operator.PLUS, new StringLiteral("world"));
        assertThat(engine.execute(node).value()).isEqualTo("hello world");
    }

    @Test
    void execute_binaryOp_stringConcatWithInt() {
        var node = new BinaryOpNode(new StringLiteral("x="), Operator.PLUS, new IntLiteral(42));
        assertThat(engine.execute(node).value()).isEqualTo("x=42");
    }

    // --- Execute unary operations ---

    @Test
    void execute_unaryOp_negate() {
        var node = new UnaryOpNode(Operator.MINUS, new IntLiteral(5), true);
        assertThat(engine.execute(node).value()).isEqualTo(-5L);
    }

    @Test
    void execute_unaryOp_logicalNot() {
        var node = new UnaryOpNode(Operator.NOT, new BoolLiteral(true), true);
        assertThat(engine.execute(node).value()).isEqualTo(false);
    }

    @Test
    void execute_unaryOp_bitwiseNot() {
        var node = new UnaryOpNode(Operator.BIT_NOT, new IntLiteral(0), true);
        assertThat(engine.execute(node).value()).isEqualTo(-1L); // ~0 == -1
    }

    // --- Execute assignment ---

    @Test
    void execute_assignment_definesNewVariable() {
        var prog = new ProgramNode(List.of(
                new AssignmentNode(new IdentifierNode("x"), new IntLiteral(42)),
                new IdentifierNode("x")
        ));
        assertThat(engine.execute(prog).value()).isEqualTo(42L);
    }

    @Test
    void execute_assignment_updatesExistingVariable() {
        var prog = new ProgramNode(List.of(
                new AssignmentNode(new IdentifierNode("x"), new IntLiteral(1)),
                new AssignmentNode(new IdentifierNode("x"), new IntLiteral(2)),
                new IdentifierNode("x")
        ));
        assertThat(engine.execute(prog).value()).isEqualTo(2L);
    }

    // --- Execute block ---

    @Test
    void execute_block_scopeEnterExit() {
        var prog = new ProgramNode(List.of(
                new AssignmentNode(new IdentifierNode("x"), new IntLiteral(1)),
                new BlockNode(List.of(
                        new AssignmentNode(new IdentifierNode("y"), new IntLiteral(2)),
                        new IdentifierNode("x") // visible from parent scope
                ))
        ));
        var result = engine.execute(prog);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(1L);
    }

    // --- Execute conditional ---

    @Test
    void execute_conditional_trueBranch() {
        var node = new ConditionalNode(new BoolLiteral(true), new IntLiteral(1), new IntLiteral(2));
        assertThat(engine.execute(node).value()).isEqualTo(1L);
    }

    @Test
    void execute_conditional_falseBranch() {
        var node = new ConditionalNode(new BoolLiteral(false), new IntLiteral(1), new IntLiteral(2));
        assertThat(engine.execute(node).value()).isEqualTo(2L);
    }

    @Test
    void execute_conditional_noElse_returnNull() {
        var node = new ConditionalNode(new BoolLiteral(false), new IntLiteral(1));
        assertThat(engine.execute(node).value()).isNull();
    }

    // --- Execute loop ---

    @Test
    void execute_loop_while() {
        // x = 0; while (x < 3) { x = x + 1; }  => x should be 3
        var prog = new ProgramNode(List.of(
                new AssignmentNode(new IdentifierNode("x"), new IntLiteral(0)),
                new LoopNode(LoopKind.WHILE,
                        new BinaryOpNode(new IdentifierNode("x"), Operator.LT, new IntLiteral(3)),
                        new AssignmentNode(new IdentifierNode("x"),
                                new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IntLiteral(1)))
                ),
                new IdentifierNode("x")
        ));
        assertThat(engine.execute(prog).value()).isEqualTo(3L);
    }

    // --- Execute function definition and call ---

    @Test
    void execute_functionDefAndCall() {
        var prog = new ProgramNode(List.of(
                new FunctionDefNode("answer", List.of(), new IntLiteral(42)),
                new FunctionCallNode("answer", List.of())
        ));
        assertThat(engine.execute(prog).value()).isEqualTo(42L);
    }

    @Test
    void execute_functionWithParameters() {
        var prog = new ProgramNode(List.of(
                new FunctionDefNode("add", List.of(new ParameterNode("a"), new ParameterNode("b")),
                        new BinaryOpNode(new IdentifierNode("a"), Operator.PLUS, new IdentifierNode("b"))
                ),
                new FunctionCallNode("add", List.of(new IntLiteral(3), new IntLiteral(4)))
        ));
        assertThat(engine.execute(prog).value()).isEqualTo(7L);
    }

    @Test
    void execute_nestedFunctionCalls() {
        var prog = new ProgramNode(List.of(
                new FunctionDefNode("double", List.of(new ParameterNode("n")),
                        new BinaryOpNode(new IdentifierNode("n"), Operator.MULTIPLY, new IntLiteral(2))
                ),
                new FunctionDefNode("quadruple", List.of(new ParameterNode("n")),
                        new FunctionCallNode("double", List.of(
                                new FunctionCallNode("double", List.of(new IdentifierNode("n")))
                        ))
                ),
                new FunctionCallNode("quadruple", List.of(new IntLiteral(3)))
        ));
        assertThat(engine.execute(prog).value()).isEqualTo(12L);
    }

    // --- Execute return from function ---

    @Test
    void execute_returnFromFunction() {
        // function earlyReturn(x) { return x * 2; 999; }
        var prog = new ProgramNode(List.of(
                new FunctionDefNode("earlyReturn", List.of(new ParameterNode("x")),
                        new BlockNode(List.of(
                                new ReturnNode(new BinaryOpNode(new IdentifierNode("x"), Operator.MULTIPLY, new IntLiteral(2))),
                                new IntLiteral(999) // should not be reached
                        ))
                ),
                new FunctionCallNode("earlyReturn", List.of(new IntLiteral(5)))
        ));
        assertThat(engine.execute(prog).value()).isEqualTo(10L);
    }

    // --- Execute index access on list/map ---

    @Test
    void execute_indexAccess_onList() {
        // Use native function to push a list, then access by index
        var eng = ExecutionEngine.builder().plugin(new BaseHandlerProvider()).build();
        eng.functions().registerNative("makeList", List.of(),
                (args, ctx) -> Result.success(List.of(10, 20, 30)));
        var prog = new ProgramNode(List.of(
                new IndexAccessNode(new FunctionCallNode("makeList", List.of()), new IntLiteral(1))
        ));
        assertThat(eng.execute(prog).value()).isEqualTo(20);
    }

    @Test
    void execute_indexAccess_onMap() {
        var eng = ExecutionEngine.builder().plugin(new BaseHandlerProvider()).build();
        eng.functions().registerNative("makeMap", List.of(),
                (args, ctx) -> Result.success(Map.of("a", 1, "b", 2)));
        var prog = new ProgramNode(List.of(
                new IndexAccessNode(new FunctionCallNode("makeMap", List.of()), new StringLiteral("b"))
        ));
        assertThat(eng.execute(prog).value()).isEqualTo(2);
    }

    // --- Recursion depth limit ---

    @Test
    void execute_recursionDepthLimit() {
        var lowRecursionConfig = new ExecutionConfig(5, 100_000, 30_000L, ExecutionConfig.ErrorMode.RESULT);
        var eng = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .config(lowRecursionConfig)
                .build();

        // Function that recurses infinitely: f(x) = f(x)
        var prog = new ProgramNode(List.of(
                new FunctionDefNode("recurse", List.of(new ParameterNode("x")),
                        new FunctionCallNode("recurse", List.of(new IdentifierNode("x")))
                ),
                new FunctionCallNode("recurse", List.of(new IntLiteral(0)))
        ));
        var result = eng.execute(prog);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().message()).contains("recursion depth");
    }

    // --- Loop iteration limit ---

    @Test
    void execute_loopIterationLimit() {
        var lowLoopConfig = new ExecutionConfig(256, 10, 30_000L, ExecutionConfig.ErrorMode.RESULT);
        var eng = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .config(lowLoopConfig)
                .build();

        // while(true) {} -- the loop handler stops after maxLoopIterations
        // The body increments x; after 10 iterations the loop exits naturally
        var prog = new ProgramNode(List.of(
                new AssignmentNode(new IdentifierNode("x"), new IntLiteral(0)),
                new LoopNode(LoopKind.WHILE, new BoolLiteral(true),
                        new AssignmentNode(new IdentifierNode("x"),
                                new BinaryOpNode(new IdentifierNode("x"), Operator.PLUS, new IntLiteral(1)))
                ),
                new IdentifierNode("x")
        ));
        // Loop exits after maxLoopIterations (10), so x should be 10
        var result = eng.execute(prog);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(10L);
    }

    // --- ExecutionListener notifications ---

    @Test
    void executionListener_notifiedOnNodeEnterAndExit() {
        var events = new ArrayList<String>();
        var eng = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .addListener(new TestListener() {
                    @Override
                    public void onNodeEnter(Object node, ExecutionContext ctx) {
                        events.add("enter:" + node.getClass().getSimpleName());
                    }
                    @Override
                    public void onNodeExit(Object node, ExecutionContext ctx, Result<Object> result) {
                        events.add("exit:" + node.getClass().getSimpleName());
                    }
                })
                .build();

        eng.execute(new IntLiteral(1));
        assertThat(events).containsExactly("enter:IntLiteral", "exit:IntLiteral");
    }

    @Test
    void executionListener_onErrorNotified() {
        var errors = new ArrayList<String>();
        var eng = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .addListener(new TestListener() {
                    @Override
                    public void onError(PexError error, ExecutionContext ctx) {
                        errors.add(error.code());
                    }
                })
                .build();

        eng.execute(new IdentifierNode("noSuchVar"));
        assertThat(errors).contains("UNDEFINED_VARIABLE");
    }

    @Test
    void executionListener_functionCallNotified() {
        var calls = new ArrayList<String>();
        var eng = ExecutionEngine.builder()
                .plugin(new BaseHandlerProvider())
                .addListener(new TestListener() {
                    @Override
                    public void onFunctionCall(String functionName, List<Object> args, ExecutionContext ctx) {
                        calls.add(functionName + "(" + args.size() + " args)");
                    }
                })
                .build();

        var prog = new ProgramNode(List.of(
                new FunctionDefNode("greet", List.of(), new StringLiteral("hi")),
                new FunctionCallNode("greet", List.of())
        ));
        eng.execute(prog);
        assertThat(calls).containsExactly("greet(0 args)");
    }

    // --- No handler for node type ---

    @Test
    void execute_noHandler_fails() {
        var bareEngine = ExecutionEngine.builder().build();
        var result = bareEngine.execute(new IntLiteral(1));
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("NO_HANDLER");
    }

    // --- Execute nested blocks with variable shadowing ---

    @Test
    void execute_nestedBlocks_variableShadowing() {
        var prog = new ProgramNode(List.of(
                new AssignmentNode(new IdentifierNode("x"), new IntLiteral(1)),
                new BlockNode(List.of(
                        new AssignmentNode(new IdentifierNode("x"), new IntLiteral(2)),
                        new IdentifierNode("x") // should be 2 (update, not shadow, since assignment updates)
                )),
                new IdentifierNode("x") // should be 2 (assignment updates parent scope)
        ));
        assertThat(engine.execute(prog).value()).isEqualTo(2L);
    }

    // --- Division by zero ---

    @Test
    void execute_divisionByZero() {
        var node = new BinaryOpNode(new IntLiteral(1), Operator.DIVIDE, new IntLiteral(0));
        var result = engine.execute(node);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("DIVISION_BY_ZERO");
    }

    // --- ReturnException ---

    @Test
    void returnException_carriesValue() {
        var ex = new ReturnException(42);
        assertThat(ex.value()).isEqualTo(42);
    }

    @Test
    void returnException_nullValue() {
        var ex = new ReturnException(null);
        assertThat(ex.value()).isNull();
    }

    // --- FunctionDef record ---

    @Test
    void functionDef_isNative_true() {
        var def = new FunctionDef("f", List.of(), null, (args, ctx) -> Result.success(null));
        assertThat(def.isNative()).isTrue();
    }

    @Test
    void functionDef_isNative_false() {
        var def = new FunctionDef("f", List.of(), new IntLiteral(0), null);
        assertThat(def.isNative()).isFalse();
    }

    // --- Statistics updated during execution ---

    @Test
    void execute_statisticsUpdated() {
        engine.execute(new IntLiteral(1));
        assertThat(engine.statistics().getNodeCount()).isGreaterThan(0);
    }

    // --- Undefined function call ---

    @Test
    void execute_undefinedFunctionCall() {
        var node = new FunctionCallNode("noSuchFn", List.of());
        var result = engine.execute(node);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo("UNDEFINED_FUNCTION");
    }

    // --- Native function execution ---

    @Test
    void execute_nativeFunctionCall() {
        engine.functions().registerNative("sum", List.of("a", "b"),
                (args, ctx) -> Result.success(((Number) args.get(0)).intValue() + ((Number) args.get(1)).intValue()));
        var prog = new ProgramNode(List.of(
                new FunctionCallNode("sum", List.of(new IntLiteral(10), new IntLiteral(20)))
        ));
        assertThat(engine.execute(prog).value()).isEqualTo(30);
    }

    // --- Bitwise operations ---

    @Test
    void execute_bitwiseAnd() {
        var node = new BinaryOpNode(new IntLiteral(0b1010), Operator.BIT_AND, new IntLiteral(0b1100));
        assertThat(engine.execute(node).value()).isEqualTo((long) 0b1000);
    }

    @Test
    void execute_bitwiseOr() {
        var node = new BinaryOpNode(new IntLiteral(0b1010), Operator.BIT_OR, new IntLiteral(0b1100));
        assertThat(engine.execute(node).value()).isEqualTo((long) 0b1110);
    }

    // --- Comparison operators ---

    @Test
    void execute_comparison_gt() {
        var node = new BinaryOpNode(new IntLiteral(5), Operator.GT, new IntLiteral(3));
        assertThat(engine.execute(node).value()).isEqualTo(true);
    }

    @Test
    void execute_comparison_le() {
        var node = new BinaryOpNode(new IntLiteral(3), Operator.LE, new IntLiteral(3));
        assertThat(engine.execute(node).value()).isEqualTo(true);
    }

    @Test
    void execute_comparison_ge() {
        var node = new BinaryOpNode(new IntLiteral(3), Operator.GE, new IntLiteral(5));
        assertThat(engine.execute(node).value()).isEqualTo(false);
    }

    // --- Null comparison ---

    @Test
    void execute_nullEquality() {
        var node = new BinaryOpNode(new NullLiteral(), Operator.EQ, new NullLiteral());
        assertThat(engine.execute(node).value()).isEqualTo(true);
    }

    @Test
    void execute_nullInequality() {
        var node = new BinaryOpNode(new NullLiteral(), Operator.NEQ, new IntLiteral(0));
        assertThat(engine.execute(node).value()).isEqualTo(true);
    }

    // --- Helper: minimal listener implementation ---

    private static abstract class TestListener implements ExecutionListener {
        @Override public void onNodeEnter(Object node, ExecutionContext ctx) {}
        @Override public void onNodeExit(Object node, ExecutionContext ctx, Result<Object> result) {}
        @Override public void onScopeEnter(String scopeName, ExecutionContext ctx) {}
        @Override public void onScopeExit(String scopeName, ExecutionContext ctx) {}
        @Override public void onFunctionCall(String functionName, List<Object> args, ExecutionContext ctx) {}
        @Override public void onError(PexError error, ExecutionContext ctx) {}
    }
}
