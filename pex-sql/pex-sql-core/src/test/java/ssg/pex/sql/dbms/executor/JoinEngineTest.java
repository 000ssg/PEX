package ssg.pex.sql.dbms.executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlExpression.*;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.Row;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JoinEngineTest {

    private JoinEngine engine;
    private List<Column> leftCols;
    private List<Column> rightCols;
    private List<Row> leftRows;
    private List<Row> rightRows;

    @BeforeEach
    void setUp() {
        engine = new JoinEngine();

        leftCols = List.of(
                new Column("users.id", SqlDataType.INTEGER, false, null, false, 0),
                new Column("users.name", SqlDataType.VARCHAR, false, null, false, 1)
        );
        rightCols = List.of(
                new Column("orders.id", SqlDataType.INTEGER, false, null, false, 0),
                new Column("orders.user_id", SqlDataType.INTEGER, false, null, false, 1),
                new Column("orders.amount", SqlDataType.DOUBLE, false, null, false, 2)
        );

        leftRows = List.of(
                new Row(new Object[]{1L, "Alice"}),
                new Row(new Object[]{2L, "Bob"}),
                new Row(new Object[]{3L, "Charlie"})
        );
        rightRows = List.of(
                new Row(new Object[]{101L, 1L, 100.0}),
                new Row(new Object[]{102L, 1L, 200.0}),
                new Row(new Object[]{103L, 2L, 150.0})
        );
    }

    private JoinClause clause(JoinType type) {
        SqlExpression on = new BinaryExpr(
                new ColumnRef(null, "users.id"),
                "=",
                new ColumnRef(null, "orders.user_id")
        );
        return new JoinClause(type, "orders", null, on);
    }

    @Test
    void innerJoinMatchesCorrectly() {
        var result = engine.join(leftRows, leftCols, rightRows, rightCols, clause(JoinType.INNER));
        assertThat(result).hasSize(3); // Alice has 2 orders, Bob has 1
    }

    @Test
    void innerJoinExcludesNonMatching() {
        var result = engine.join(leftRows, leftCols, rightRows, rightCols, clause(JoinType.INNER));
        // Charlie (id=3) has no orders, so excluded
        for (Row row : result) {
            assertThat(row.getValue(1)).isNotEqualTo("Charlie");
        }
    }

    @Test
    void leftJoinKeepsAllLeftRows() {
        var result = engine.join(leftRows, leftCols, rightRows, rightCols, clause(JoinType.LEFT));
        assertThat(result).hasSize(4); // Alice(2) + Bob(1) + Charlie(1, with nulls)
    }

    @Test
    void leftJoinNullsForUnmatched() {
        var result = engine.join(leftRows, leftCols, rightRows, rightCols, clause(JoinType.LEFT));
        // Find Charlie's row
        var charlieRow = result.stream()
                .filter(r -> "Charlie".equals(r.getValue(1)))
                .findFirst()
                .orElse(null);
        assertThat(charlieRow).isNotNull();
        assertThat(charlieRow.getValue(2)).isNull(); // orders.id is null
    }

    @Test
    void rightJoinKeepsAllRightRows() {
        var result = engine.join(leftRows, leftCols, rightRows, rightCols, clause(JoinType.RIGHT));
        assertThat(result).hasSize(3); // All orders have matching users
    }

    @Test
    void rightJoinWithUnmatchedRightRows() {
        var extraRightRows = List.of(
                new Row(new Object[]{101L, 1L, 100.0}),
                new Row(new Object[]{104L, 99L, 300.0}) // no user with id=99
        );
        var result = engine.join(leftRows, leftCols, extraRightRows, rightCols, clause(JoinType.RIGHT));
        assertThat(result).hasSize(2); // 1 matched + 1 unmatched
    }

    @Test
    void fullJoinKeepsAll() {
        var result = engine.join(leftRows, leftCols, rightRows, rightCols, clause(JoinType.FULL));
        assertThat(result).hasSize(4); // 3 matched + 1 unmatched left (Charlie)
    }

    @Test
    void fullJoinWithUnmatchedBothSides() {
        var extraRightRows = List.of(
                new Row(new Object[]{101L, 1L, 100.0}),
                new Row(new Object[]{104L, 99L, 300.0})
        );
        var result = engine.join(leftRows, leftCols, extraRightRows, rightCols, clause(JoinType.FULL));
        // Alice matches 1 order, Bob+Charlie unmatched left, id=99 unmatched right
        assertThat(result).hasSize(4);
    }

    @Test
    void crossJoinCartesianProduct() {
        var crossClause = new JoinClause(JoinType.CROSS, "orders", null, null);
        var result = engine.join(leftRows, leftCols, rightRows, rightCols, crossClause);
        assertThat(result).hasSize(9); // 3 * 3
    }

    @Test
    void crossJoinWithEmptyRight() {
        var crossClause = new JoinClause(JoinType.CROSS, "orders", null, null);
        var result = engine.join(leftRows, leftCols, List.of(), rightCols, crossClause);
        assertThat(result).isEmpty();
    }

    @Test
    void innerJoinWithEmptyTables() {
        var result = engine.join(List.of(), leftCols, rightRows, rightCols, clause(JoinType.INNER));
        assertThat(result).isEmpty();
    }

    @Test
    void mergedRowHasCorrectColumnCount() {
        var result = engine.join(leftRows, leftCols, rightRows, rightCols, clause(JoinType.INNER));
        assertThat(result.getFirst().columnCount()).isEqualTo(5); // 2 left + 3 right
    }

    @Test
    void innerJoinPreservesOrder() {
        var result = engine.join(leftRows, leftCols, rightRows, rightCols, clause(JoinType.INNER));
        // First results should be for Alice (id=1)
        assertThat(result.getFirst().getValue(1)).isEqualTo("Alice");
    }

    @Test
    void leftJoinWithAllMatches() {
        var allMatchRight = List.of(
                new Row(new Object[]{101L, 1L, 100.0}),
                new Row(new Object[]{102L, 2L, 200.0}),
                new Row(new Object[]{103L, 3L, 300.0})
        );
        var result = engine.join(leftRows, leftCols, allMatchRight, rightCols, clause(JoinType.LEFT));
        assertThat(result).hasSize(3);
    }

    @Test
    void leftJoinWithNoMatches() {
        var noMatchRight = List.of(
                new Row(new Object[]{101L, 99L, 100.0})
        );
        var result = engine.join(leftRows, leftCols, noMatchRight, rightCols, clause(JoinType.LEFT));
        assertThat(result).hasSize(3); // All left rows, each with null right side
    }

    @Test
    void crossJoinSingleElements() {
        var singleLeft = List.of(new Row(new Object[]{1L, "Alice"}));
        var singleRight = List.of(new Row(new Object[]{101L, 1L, 100.0}));
        var crossClause = new JoinClause(JoinType.CROSS, "orders", null, null);
        var result = engine.join(singleLeft, leftCols, singleRight, rightCols, crossClause);
        assertThat(result).hasSize(1);
    }

    @Test
    void innerJoinWithNoCondition() {
        var noConditionClause = new JoinClause(JoinType.INNER, "orders", null, null);
        var result = engine.join(leftRows, leftCols, rightRows, rightCols, noConditionClause);
        assertThat(result).hasSize(9); // Becomes cross join
    }

    @Test
    void rightJoinNullsForUnmatchedLeft() {
        var extraRightRows = List.of(
                new Row(new Object[]{104L, 99L, 300.0})
        );
        var result = engine.join(leftRows, leftCols, extraRightRows, rightCols, clause(JoinType.RIGHT));
        // The unmatched right row should have null left columns
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getValue(0)).isNull();
    }
}
