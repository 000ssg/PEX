package ssg.pex.sql.olap;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import ssg.pex.sql.olap.ast.*;
import ssg.pex.sql.olap.executor.WindowFunctionExecutor;
import ssg.pex.sql.ast.SqlSupport.OrderByClause;
import ssg.pex.sql.ast.SqlSupport.OrderByItem;

import java.util.List;

class OlapAstTest {

    // ── FrameBound ──────────────────────────────────────────────

    @Test
    void unboundedPreceding() {
        FrameBound fb = FrameBound.unboundedPreceding();
        assertThat(fb.type()).isEqualTo(FrameBound.BoundType.UNBOUNDED_PRECEDING);
    }

    @Test
    void currentRow() {
        FrameBound fb = FrameBound.currentRow();
        assertThat(fb.type()).isEqualTo(FrameBound.BoundType.CURRENT_ROW);
    }

    @Test
    void preceding() {
        FrameBound fb = FrameBound.preceding(5);
        assertThat(fb.offset()).isEqualTo(5);
    }

    @Test
    void following() {
        FrameBound fb = FrameBound.following(3);
        assertThat(fb.offset()).isEqualTo(3);
    }

    @Test
    void boundTypeValues() {
        assertThat(FrameBound.BoundType.values()).hasSize(5);
    }

    // ── FrameSpec ───────────────────────────────────────────────

    @Test
    void frameSpecRows() {
        FrameSpec fs = new FrameSpec(FrameSpec.FrameType.ROWS,
                FrameBound.unboundedPreceding(), FrameBound.currentRow());
        assertThat(fs.type()).isEqualTo(FrameSpec.FrameType.ROWS);
    }

    // ── OverClause ──────────────────────────────────────────────

    @Test
    void overClauseBasic() {
        OverClause oc = new OverClause(List.of("dept"), null, null);
        assertThat(oc.partitionBy()).containsExactly("dept");
        assertThat(oc.orderBy()).isNull();
        assertThat(oc.frame()).isNull();
    }

    @Test
    void overClauseWithFrame() {
        FrameSpec frame = new FrameSpec(FrameSpec.FrameType.ROWS,
                FrameBound.unboundedPreceding(), FrameBound.currentRow());
        OrderByClause ob = new OrderByClause(List.of(new OrderByItem("sal", true, false)));
        OverClause oc = new OverClause(List.of("dept"), ob, frame);
        assertThat(oc.orderBy()).isNotNull();
        assertThat(oc.frame()).isSameAs(frame);
    }

    // ── WindowFunctionExecutor ──────────────────────────────────

    @Test
    void isWindowFunctionTrue() {
        assertThat(WindowFunctionExecutor.isWindowFunction("ROW_NUMBER")).isTrue();
        assertThat(WindowFunctionExecutor.isWindowFunction("row_number")).isTrue();
        assertThat(WindowFunctionExecutor.isWindowFunction("RANK")).isTrue();
        assertThat(WindowFunctionExecutor.isWindowFunction("DENSE_RANK")).isTrue();
        assertThat(WindowFunctionExecutor.isWindowFunction("NTILE")).isTrue();
        assertThat(WindowFunctionExecutor.isWindowFunction("LAG")).isTrue();
        assertThat(WindowFunctionExecutor.isWindowFunction("LEAD")).isTrue();
    }

    @Test
    void isWindowFunctionAggregate() {
        assertThat(WindowFunctionExecutor.isWindowFunction("SUM")).isTrue();
        assertThat(WindowFunctionExecutor.isWindowFunction("COUNT")).isTrue();
        assertThat(WindowFunctionExecutor.isWindowFunction("AVG")).isTrue();
        assertThat(WindowFunctionExecutor.isWindowFunction("MIN")).isTrue();
        assertThat(WindowFunctionExecutor.isWindowFunction("MAX")).isTrue();
    }

    @Test
    void isWindowFunctionFalse() {
        assertThat(WindowFunctionExecutor.isWindowFunction("CUSTOM")).isFalse();
    }

    // ── GroupingSetSpec ─────────────────────────────────────────

    @Test
    void groupingSets() {
        GroupingSetSpec gss = new GroupingSetSpec(
                GroupingSetSpec.GroupingType.GROUPING_SETS,
                List.of(List.of("a"), List.of("b")), null);
        assertThat(gss.type()).isEqualTo(GroupingSetSpec.GroupingType.GROUPING_SETS);
        assertThat(gss.sets()).hasSize(2);
    }

    @Test
    void rollup() {
        GroupingSetSpec gss = new GroupingSetSpec(
                GroupingSetSpec.GroupingType.ROLLUP,
                List.of(List.of("a", "b")), null);
        assertThat(gss.type()).isEqualTo(GroupingSetSpec.GroupingType.ROLLUP);
    }

    @Test
    void groupingTypeValues() {
        assertThat(GroupingSetSpec.GroupingType.values()).hasSize(3);
    }

    // ── MergeAction ─────────────────────────────────────────────

    @Test
    void whenMatchedUpdate() {
        var action = new MergeAction.WhenMatchedUpdate(java.util.Map.of());
        assertThat(action.setColumns()).isEmpty();
    }

    @Test
    void whenNotMatchedInsert() {
        var action = new MergeAction.WhenNotMatchedInsert(List.of("a"), List.of());
        assertThat(action.columns()).containsExactly("a");
    }

    @Test
    void whenMatchedDelete() {
        var action = new MergeAction.WhenMatchedDelete();
        // verify construction
    }

    // ── PivotClause ─────────────────────────────────────────────

    @Test
    void pivotClause() {
        PivotClause pc = new PivotClause("SUM", "sales", "quarter", List.of("Q1", "Q2"), null);
        assertThat(pc.aggregateFunction()).isEqualTo("SUM");
        assertThat(pc.aggregateColumn()).isEqualTo("sales");
    }

    // ── WithClause ──────────────────────────────────────────────

    @Test
    void withClauseNonRecursive() {
        WithClause wc = new WithClause(List.of(), false, null);
        assertThat(wc.recursive()).isFalse();
        assertThat(wc.ctes()).isEmpty();
    }

    @Test
    void withClauseRecursive() {
        WithClause wc = new WithClause(List.of(), true, null);
        assertThat(wc.recursive()).isTrue();
    }
}
