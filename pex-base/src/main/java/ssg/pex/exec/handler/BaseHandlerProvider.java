package ssg.pex.exec.handler;

import ssg.pex.ast.node.AssignmentNode;
import ssg.pex.ast.node.BinaryOpNode;
import ssg.pex.ast.node.BlockNode;
import ssg.pex.ast.node.BoolLiteral;
import ssg.pex.ast.node.ConditionalNode;
import ssg.pex.ast.node.FloatLiteral;
import ssg.pex.ast.node.FunctionCallNode;
import ssg.pex.ast.node.FunctionDefNode;
import ssg.pex.ast.node.IdentifierNode;
import ssg.pex.ast.node.IndexAccessNode;
import ssg.pex.ast.node.IntLiteral;
import ssg.pex.ast.node.LiteralNode;
import ssg.pex.ast.node.LoopNode;
import ssg.pex.ast.node.NullLiteral;
import ssg.pex.ast.node.ProgramNode;
import ssg.pex.ast.node.ReturnNode;
import ssg.pex.ast.node.StringLiteral;
import ssg.pex.ast.node.UnaryOpNode;
import ssg.pex.exec.NodeHandler;
import ssg.pex.spi.HandlerProvider;
import ssg.pex.spi.PexPlugin;
import ssg.pex.spi.PluginContext;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BaseHandlerProvider implements PexPlugin, HandlerProvider {

    @Override
    public String name() {
        return "pex-base-handlers";
    }

    @Override
    public int loadOrder() {
        return 0; // Base handlers load first
    }

    @Override
    public void initialize(PluginContext ctx) {
        for (var entry : provideHandlers().entrySet()) {
            ctx.registerHandler(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Map<Class<?>, NodeHandler> provideHandlers() {
        var handlers = new LinkedHashMap<Class<?>, NodeHandler>();

        var literalHandler = new LiteralHandler();
        handlers.put(LiteralNode.class, literalHandler);
        handlers.put(IntLiteral.class, literalHandler);
        handlers.put(FloatLiteral.class, literalHandler);
        handlers.put(StringLiteral.class, literalHandler);
        handlers.put(BoolLiteral.class, literalHandler);
        handlers.put(NullLiteral.class, literalHandler);

        handlers.put(IdentifierNode.class, new IdentifierHandler());
        handlers.put(BinaryOpNode.class, new BinaryOpHandler());
        handlers.put(UnaryOpNode.class, new UnaryOpHandler());
        handlers.put(AssignmentNode.class, new AssignmentHandler());
        handlers.put(BlockNode.class, new BlockHandler());
        handlers.put(ConditionalNode.class, new ConditionalHandler());
        handlers.put(LoopNode.class, new LoopHandler());
        handlers.put(FunctionDefNode.class, new FunctionDefHandler());
        handlers.put(FunctionCallNode.class, new FunctionCallHandler());
        handlers.put(ReturnNode.class, new ReturnHandler());
        handlers.put(IndexAccessNode.class, new IndexAccessHandler());
        handlers.put(ProgramNode.class, new ProgramHandler());

        return Map.copyOf(handlers);
    }
}
