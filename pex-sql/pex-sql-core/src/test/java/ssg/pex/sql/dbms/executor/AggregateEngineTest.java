package ssg.pex.sql.dbms.executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.ast.SqlExpression.AggregateFunction;
import ssg.pex.sql.ast.SqlSupport.SqlDataType;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.Row;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AggregateEngineTest {

    private AggregateEngine engine;
    private List<Column> columns;
    private List<Row> rows;

    @BeforeEach
    void setUp() {
        engine = new AggregateEngine();
        columns = List.of(
                new Column("dept", SqlDataType.VARCHAR, false, null, false, 0),
                new Column("salary", SqlDataType.DOUBLE, false, null, false, 1),
                new Column("name", SqlDataType.VARCHAR, false, null, false, 2)
        );
        rows = List.of(
                new Row(new Object[]{"IT", 50000.0, "Alice"}),
                new Row(new Object[]{"IT", 60000.0, "Bob"}),
                new Row(new Object[]{"HR", 45000.0, "Charlie"}),
                new Row(new Object[]{"HR", 55000.0, "Diana"}),
                new Row(new Object[]{"IT", 70000.0, "Eve"})
        );
    }

    @Test
    void groupByDepartment() {
        var groups = engine.groupRows(rows, List.of("dept"), columns);
        assertThat(groups).hasSize(2);
        assertThat(groups.get(List.of("IT"))).hasSize(3);
        assertThat(groups.get(List.of("HR"))).hasSize(2);
    }

    @Test
    void countNonNull() {
        var values = Arrays.<Object>asList(1L, 2L, 3L, null, 5L);
        var result = engine.computeAggregate(AggregateFunction.COUNT, values, false);
        assertThat(result).isEqualTo(4L);
    }

    @Test
    void countAll() {
        var values = List.<Object>of(1L, 2L, 3L);
        var result = engine.computeAggregate(AggregateFunction.COUNT, values, false);
        assertThat(result).isEqualTo(3L);
    }

    @Test
    void countDistinct() {
        var values = List.<Object>of(1L, 1L, 2L, 2L, 3L);
        var result = engine.computeAggregate(AggregateFunction.COUNT, values, true);
        assertThat(result).isEqualTo(3L);
    }

    @Test
    void sumIntegers() {
        var values = List.<Object>of(10L, 20L, 30L);
        var result = engine.computeAggregate(AggregateFunction.SUM, values, false);
        assertThat(result).isEqualTo(60L);
    }

    @Test
    void sumDoubles() {
        var values = List.<Object>of(10.5, 20.3, 30.2);
        var result = engine.computeAggregate(AggregateFunction.SUM, values, false);
        assertThat((Double) result).isCloseTo(61.0, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void sumDistinct() {
        var values = List.<Object>of(10L, 10L, 20L, 20L, 30L);
        var result = engine.computeAggregate(AggregateFunction.SUM, values, true);
        assertThat(result).isEqualTo(60L);
    }

    @Test
    void avgValues() {
        var values = List.<Object>of(10L, 20L, 30L);
        var result = engine.computeAggregate(AggregateFunction.AVG, values, false);
        assertThat((Double) result).isCloseTo(20.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void avgWithNulls() {
        var values = Arrays.<Object>asList(10L, null, 30L);
        var result = engine.computeAggregate(AggregateFunction.AVG, values, false);
        assertThat((Double) result).isCloseTo(20.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void avgDistinct() {
        var values = List.<Object>of(10L, 10L, 20L, 20L);
        var result = engine.computeAggregate(AggregateFunction.AVG, values, true);
        assertThat((Double) result).isCloseTo(15.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void minValue() {
        var values = List.<Object>of(30L, 10L, 20L);
        var result = engine.computeAggregate(AggregateFunction.MIN, values, false);
        assertThat(result).isEqualTo(10L);
    }

    @Test
    void minWithNulls() {
        var values = Arrays.<Object>asList(null, 30L, 10L);
        var result = engine.computeAggregate(AggregateFunction.MIN, values, false);
        assertThat(result).isEqualTo(10L);
    }

    @Test
    void maxValue() {
        var values = List.<Object>of(10L, 30L, 20L);
        var result = engine.computeAggregate(AggregateFunction.MAX, values, false);
        assertThat(result).isEqualTo(30L);
    }

    @Test
    void maxWithNulls() {
        var values = Arrays.<Object>asList(null, 30L, 10L);
        var result = engine.computeAggregate(AggregateFunction.MAX, values, false);
        assertThat(result).isEqualTo(30L);
    }

    @Test
    void groupConcat() {
        var values = List.<Object>of("Alice", "Bob", "Charlie");
        var result = engine.computeAggregate(AggregateFunction.GROUP_CONCAT, values, false);
        assertThat(result).isEqualTo("Alice,Bob,Charlie");
    }

    @Test
    void groupConcatDistinct() {
        var values = List.<Object>of("Alice", "Alice", "Bob");
        var result = engine.computeAggregate(AggregateFunction.GROUP_CONCAT, values, true);
        assertThat(result).isEqualTo("Alice,Bob");
    }

    @Test
    void emptyGroupCount() {
        var result = engine.computeAggregate(AggregateFunction.COUNT, List.of(), false);
        assertThat(result).isEqualTo(0L);
    }

    @Test
    void emptyGroupSum() {
        var result = engine.computeAggregate(AggregateFunction.SUM, List.of(), false);
        assertThat(result).isEqualTo(0L);
    }

    @Test
    void emptyGroupAvg() {
        var result = engine.computeAggregate(AggregateFunction.AVG, List.of(), false);
        assertThat(result).isNull();
    }

    @Test
    void emptyGroupMin() {
        var result = engine.computeAggregate(AggregateFunction.MIN, List.of(), false);
        assertThat(result).isNull();
    }

    @Test
    void emptyGroupMax() {
        var result = engine.computeAggregate(AggregateFunction.MAX, List.of(), false);
        assertThat(result).isNull();
    }

    @Test
    void extractColumnValues() {
        var values = engine.extractColumnValues(rows, 1);
        assertThat(values).containsExactly(50000.0, 60000.0, 45000.0, 55000.0, 70000.0);
    }

    @Test
    void groupByMultipleColumns() {
        var multiRows = List.of(
                new Row(new Object[]{"IT", 50000.0, "Alice"}),
                new Row(new Object[]{"IT", 50000.0, "Bob"}),
                new Row(new Object[]{"HR", 45000.0, "Charlie"})
        );
        var groups = engine.groupRows(multiRows, List.of("dept", "salary"), columns);
        assertThat(groups).hasSize(2);
    }

    @Test
    void sumWithMixedNullsAndValues() {
        var values = Arrays.<Object>asList(null, 10L, null, 20L, null);
        var result = engine.computeAggregate(AggregateFunction.SUM, values, false);
        assertThat(result).isEqualTo(30L);
    }
}
