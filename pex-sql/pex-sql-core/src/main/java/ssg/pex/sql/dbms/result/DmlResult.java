package ssg.pex.sql.dbms.result;

import java.util.List;

public record DmlResult(int affectedRows, List<Object> generatedKeys) {

    public static DmlResult of(int affectedRows) {
        return new DmlResult(affectedRows, List.of());
    }

    public static DmlResult of(int affectedRows, List<Object> generatedKeys) {
        return new DmlResult(affectedRows, generatedKeys);
    }
}
