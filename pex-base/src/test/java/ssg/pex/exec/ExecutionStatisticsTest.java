package ssg.pex.exec;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.util.Map;

class ExecutionStatisticsTest {

    @Test
    void initialZero() {
        ExecutionStatistics stats = new ExecutionStatistics();
        assertThat(stats.getNodeCount()).isZero();
        assertThat(stats.getFunctionCalls()).isZero();
        assertThat(stats.getScopeEntries()).isZero();
        assertThat(stats.getScopeExits()).isZero();
        assertThat(stats.getErrors()).isZero();
    }

    @Test
    void incrementNodeCount() {
        ExecutionStatistics stats = new ExecutionStatistics();
        stats.incrementNodeCount();
        stats.incrementNodeCount();
        assertThat(stats.getNodeCount()).isEqualTo(2);
    }

    @Test
    void incrementFunctionCalls() {
        ExecutionStatistics stats = new ExecutionStatistics();
        stats.incrementFunctionCalls();
        assertThat(stats.getFunctionCalls()).isEqualTo(1);
    }

    @Test
    void incrementScopeEntries() {
        ExecutionStatistics stats = new ExecutionStatistics();
        stats.incrementScopeEntries();
        assertThat(stats.getScopeEntries()).isEqualTo(1);
    }

    @Test
    void incrementScopeExits() {
        ExecutionStatistics stats = new ExecutionStatistics();
        stats.incrementScopeExits();
        assertThat(stats.getScopeExits()).isEqualTo(1);
    }

    @Test
    void incrementErrors() {
        ExecutionStatistics stats = new ExecutionStatistics();
        stats.incrementErrors();
        assertThat(stats.getErrors()).isEqualTo(1);
    }

    @Test
    void snapshot() {
        ExecutionStatistics stats = new ExecutionStatistics();
        stats.incrementNodeCount();
        stats.incrementFunctionCalls();
        stats.incrementErrors();
        Map<String, Long> snap = stats.snapshot();
        assertThat(snap.get("nodeCount")).isEqualTo(1L);
        assertThat(snap.get("functionCalls")).isEqualTo(1L);
        assertThat(snap.get("errors")).isEqualTo(1L);
        assertThat(snap.get("scopeEntries")).isEqualTo(0L);
        assertThat(snap.get("scopeExits")).isEqualTo(0L);
    }

    @Test
    void reset() {
        ExecutionStatistics stats = new ExecutionStatistics();
        stats.incrementNodeCount();
        stats.incrementFunctionCalls();
        stats.reset();
        assertThat(stats.getNodeCount()).isZero();
        assertThat(stats.getFunctionCalls()).isZero();
    }
}
