package ssg.pex.sql.dbms.executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlExpression.*;
import ssg.pex.sql.ast.SqlSupport.SqlDataType;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.Row;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionEvaluatorTest {

    private ExpressionEvaluator evaluator;
    private List<Column> columns;
    private Row row;

    @BeforeEach
    void setUp() {
        evaluator = new ExpressionEvaluator();
        columns = List.of(
                new Column("id", SqlDataType.INTEGER, false, null, false, 0),
                new Column("name", SqlDataType.VARCHAR, true, null, false, 1),
                new Column("age", SqlDataType.INTEGER, true, null, false, 2),
                new Column("salary", SqlDataType.DOUBLE, true, null, false, 3),
                new Column("active", SqlDataType.BOOLEAN, true, null, false, 4)
        );
        row = new Row(new Object[]{1L, "Alice", 30L, 50000.0, true});
    }

    @Test
    void evaluateColumnRef() {
        var expr = new ColumnRef(null, "name");
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("Alice");
    }

    @Test
    void evaluateColumnRefById() {
        var expr = new ColumnRef(null, "id");
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(1L);
    }

    @Test
    void evaluateLiteralString() {
        var expr = new LiteralExpr("hello");
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("hello");
    }

    @Test
    void evaluateLiteralNumber() {
        var expr = new LiteralExpr(42L);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(42L);
    }

    @Test
    void evaluateLiteralNull() {
        var expr = new LiteralExpr(null);
        assertThat(evaluator.evaluate(expr, row, columns)).isNull();
    }

    @Test
    void evaluateEqualityTrue() {
        var expr = new BinaryExpr(new ColumnRef(null, "name"), "=", new LiteralExpr("Alice"));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateEqualityFalse() {
        var expr = new BinaryExpr(new ColumnRef(null, "name"), "=", new LiteralExpr("Bob"));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(false);
    }

    @Test
    void evaluateGreaterThan() {
        var expr = new BinaryExpr(new ColumnRef(null, "age"), ">", new LiteralExpr(18L));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateLessThan() {
        var expr = new BinaryExpr(new ColumnRef(null, "age"), "<", new LiteralExpr(18L));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(false);
    }

    @Test
    void evaluateNotEqual() {
        var expr = new BinaryExpr(new ColumnRef(null, "id"), "<>", new LiteralExpr(2L));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateAddition() {
        var expr = new BinaryExpr(new ColumnRef(null, "age"), "+", new LiteralExpr(5L));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(35L);
    }

    @Test
    void evaluateSubtraction() {
        var expr = new BinaryExpr(new ColumnRef(null, "age"), "-", new LiteralExpr(10L));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(20L);
    }

    @Test
    void evaluateMultiplication() {
        var expr = new BinaryExpr(new LiteralExpr(6L), "*", new LiteralExpr(7L));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(42L);
    }

    @Test
    void evaluateDivision() {
        var expr = new BinaryExpr(new LiteralExpr(100L), "/", new LiteralExpr(4L));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(25L);
    }

    @Test
    void evaluateModulo() {
        var expr = new BinaryExpr(new LiteralExpr(10L), "%", new LiteralExpr(3L));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(1L);
    }

    @Test
    void evaluateAndTrue() {
        var expr = new BinaryExpr(
                new BinaryExpr(new ColumnRef(null, "age"), ">", new LiteralExpr(18L)),
                "AND",
                new BinaryExpr(new ColumnRef(null, "active"), "=", new LiteralExpr(true))
        );
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateAndFalse() {
        var expr = new BinaryExpr(
                new BinaryExpr(new ColumnRef(null, "age"), ">", new LiteralExpr(50L)),
                "AND",
                new LiteralExpr(true)
        );
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(false);
    }

    @Test
    void evaluateOrTrue() {
        var expr = new BinaryExpr(
                new BinaryExpr(new ColumnRef(null, "age"), ">", new LiteralExpr(50L)),
                "OR",
                new BinaryExpr(new ColumnRef(null, "name"), "=", new LiteralExpr("Alice"))
        );
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateNot() {
        var expr = new UnaryExpr("NOT", new LiteralExpr(false));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateUnaryMinus() {
        var expr = new UnaryExpr("-", new LiteralExpr(42L));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(-42L);
    }

    @Test
    void evaluateFunctionUpper() {
        var expr = new FunctionExpr("UPPER", List.of(new ColumnRef(null, "name")));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("ALICE");
    }

    @Test
    void evaluateFunctionLower() {
        var expr = new FunctionExpr("LOWER", List.of(new LiteralExpr("HELLO")));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("hello");
    }

    @Test
    void evaluateFunctionLength() {
        var expr = new FunctionExpr("LENGTH", List.of(new ColumnRef(null, "name")));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(5L);
    }

    @Test
    void evaluateFunctionCoalesce() {
        var expr = new FunctionExpr("COALESCE", List.of(new LiteralExpr(null), new LiteralExpr("default")));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("default");
    }

    @Test
    void evaluateFunctionConcat() {
        var expr = new FunctionExpr("CONCAT", List.of(new LiteralExpr("Hello"), new LiteralExpr(" "), new LiteralExpr("World")));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("Hello World");
    }

    @Test
    void evaluateInTrue() {
        var expr = new InExpr(new ColumnRef(null, "id"),
                List.of(new LiteralExpr(1L), new LiteralExpr(2L), new LiteralExpr(3L)), false);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateInFalse() {
        var expr = new InExpr(new ColumnRef(null, "id"),
                List.of(new LiteralExpr(10L), new LiteralExpr(20L)), false);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(false);
    }

    @Test
    void evaluateNotIn() {
        var expr = new InExpr(new ColumnRef(null, "id"),
                List.of(new LiteralExpr(10L), new LiteralExpr(20L)), true);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateBetweenTrue() {
        var expr = new BetweenExpr(new ColumnRef(null, "age"),
                new LiteralExpr(18L), new LiteralExpr(65L), false);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateBetweenFalse() {
        var expr = new BetweenExpr(new ColumnRef(null, "age"),
                new LiteralExpr(40L), new LiteralExpr(50L), false);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(false);
    }

    @Test
    void evaluateNotBetween() {
        var expr = new BetweenExpr(new ColumnRef(null, "age"),
                new LiteralExpr(40L), new LiteralExpr(50L), true);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateLikeMatch() {
        var expr = new LikeExpr(new ColumnRef(null, "name"), "Ali%", false);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateLikeNoMatch() {
        var expr = new LikeExpr(new ColumnRef(null, "name"), "Bob%", false);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(false);
    }

    @Test
    void evaluateNotLike() {
        var expr = new LikeExpr(new ColumnRef(null, "name"), "Bob%", true);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateIsNull() {
        var nullRow = new Row(new Object[]{1L, null, 30L, 50000.0, true});
        var expr = new IsNullExpr(new ColumnRef(null, "name"), false);
        assertThat(evaluator.evaluate(expr, nullRow, columns)).isEqualTo(true);
    }

    @Test
    void evaluateIsNotNull() {
        var expr = new IsNullExpr(new ColumnRef(null, "name"), true);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateCaseSearched() {
        var expr = new CaseExpr(null, List.of(
                new WhenClause(new BinaryExpr(new ColumnRef(null, "age"), ">", new LiteralExpr(18L)), new LiteralExpr("adult")),
                new WhenClause(new LiteralExpr(true), new LiteralExpr("minor"))
        ), null);
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("adult");
    }

    @Test
    void evaluateCaseSimple() {
        var expr = new CaseExpr(new ColumnRef(null, "name"), List.of(
                new WhenClause(new LiteralExpr("Alice"), new LiteralExpr("Found Alice")),
                new WhenClause(new LiteralExpr("Bob"), new LiteralExpr("Found Bob"))
        ), new LiteralExpr("Unknown"));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("Found Alice");
    }

    @Test
    void evaluateCaseElse() {
        var expr = new CaseExpr(new ColumnRef(null, "name"), List.of(
                new WhenClause(new LiteralExpr("Bob"), new LiteralExpr("Found Bob"))
        ), new LiteralExpr("Unknown"));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("Unknown");
    }

    @Test
    void evaluateNullComparison() {
        var expr = new BinaryExpr(new LiteralExpr(null), "=", new LiteralExpr(null));
        // NULL = NULL returns true in our simplified implementation
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(true);
    }

    @Test
    void evaluateConcatOperator() {
        var expr = new BinaryExpr(new LiteralExpr("Hello"), "||", new LiteralExpr(" World"));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("Hello World");
    }

    @Test
    void evaluateFunctionTrim() {
        var expr = new FunctionExpr("TRIM", List.of(new LiteralExpr("  hello  ")));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("hello");
    }

    @Test
    void evaluateFunctionAbs() {
        var expr = new FunctionExpr("ABS", List.of(new LiteralExpr(-42L)));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo(42L);
    }

    @Test
    void evaluateFunctionReplace() {
        var expr = new FunctionExpr("REPLACE", List.of(
                new LiteralExpr("hello world"), new LiteralExpr("world"), new LiteralExpr("there")));
        assertThat(evaluator.evaluate(expr, row, columns)).isEqualTo("hello there");
    }

    @Test
    void evaluateFunctionNullif() {
        var expr = new FunctionExpr("NULLIF", List.of(new LiteralExpr(1L), new LiteralExpr(1L)));
        assertThat(evaluator.evaluate(expr, row, columns)).isNull();
    }

    @Test
    void evaluateFloatingPointArithmetic() {
        var expr = new BinaryExpr(new LiteralExpr(10.5), "+", new LiteralExpr(2.3));
        assertThat((Double) evaluator.evaluate(expr, row, columns)).isCloseTo(12.8, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void evaluateDivisionByZero() {
        var expr = new BinaryExpr(new LiteralExpr(10L), "/", new LiteralExpr(0L));
        assertThat(evaluator.evaluate(expr, row, columns)).isNull();
    }
}
