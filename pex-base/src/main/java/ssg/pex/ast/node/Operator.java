package ssg.pex.ast.node;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Operator {

    // Arithmetic
    PLUS("+", 11),
    MINUS("-", 11),
    MULTIPLY("*", 12),
    DIVIDE("/", 12),
    MODULO("%", 12),

    // Comparison
    EQ("==", 8),
    NEQ("!=", 8),
    LT("<", 9),
    GT(">", 9),
    LE("<=", 9),
    GE(">=", 9),

    // Logical
    AND("&&", 4),
    OR("||", 3),
    NOT("!", 14),

    // Bitwise
    BIT_AND("&", 7),
    BIT_OR("|", 5),
    BIT_XOR("^", 6),
    BIT_NOT("~", 14),
    SHIFT_LEFT("<<", 10),
    SHIFT_RIGHT(">>", 10),
    UNSIGNED_SHIFT_RIGHT(">>>", 10),

    // Assignment
    ASSIGN("=", 1);

    private final String symbol;
    private final int precedence;

    private static final Map<String, Operator> BY_SYMBOL =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(Operator::symbol, Function.identity()));

    Operator(String symbol, int precedence) {
        this.symbol = symbol;
        this.precedence = precedence;
    }

    public String symbol() {
        return symbol;
    }

    public int precedence() {
        return precedence;
    }

    public static Optional<Operator> fromSymbol(String s) {
        return Optional.ofNullable(BY_SYMBOL.get(s));
    }

    public boolean isUnary() {
        return this == NOT || this == BIT_NOT || this == PLUS || this == MINUS;
    }

    public boolean isBinary() {
        return this != NOT && this != BIT_NOT;
    }

    public boolean isComparison() {
        return switch (this) {
            case EQ, NEQ, LT, GT, LE, GE -> true;
            default -> false;
        };
    }

    public boolean isLogical() {
        return switch (this) {
            case AND, OR, NOT -> true;
            default -> false;
        };
    }

    public boolean isBitwise() {
        return switch (this) {
            case BIT_AND, BIT_OR, BIT_XOR, BIT_NOT, SHIFT_LEFT, SHIFT_RIGHT, UNSIGNED_SHIFT_RIGHT -> true;
            default -> false;
        };
    }

    public boolean isArithmetic() {
        return switch (this) {
            case PLUS, MINUS, MULTIPLY, DIVIDE, MODULO -> true;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return symbol;
    }
}
