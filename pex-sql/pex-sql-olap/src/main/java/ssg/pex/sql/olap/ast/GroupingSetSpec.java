package ssg.pex.sql.olap.ast;

import ssg.pex.ast.SourceLocation;

import java.util.List;

/**
 * CUBE, ROLLUP, or GROUPING SETS specification.
 */
public record GroupingSetSpec(
        GroupingType type,
        List<List<String>> sets,
        SourceLocation location
) implements OlapNode {

    public enum GroupingType {
        CUBE, ROLLUP, GROUPING_SETS
    }

    public GroupingSetSpec {
        sets = List.copyOf(sets.stream().map(List::copyOf).toList());
    }
}
