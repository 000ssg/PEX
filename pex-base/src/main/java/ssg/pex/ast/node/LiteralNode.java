package ssg.pex.ast.node;

public sealed interface LiteralNode extends AstNode permits
        IntLiteral,
        FloatLiteral,
        StringLiteral,
        BoolLiteral,
        NullLiteral {

    Object literalValue();
}
