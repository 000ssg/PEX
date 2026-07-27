package ssg.pex.sql.dbms;

import ssg.pex.sql.ast.SqlSupport.SqlDataType;

public record Column(String name, SqlDataType dataType, boolean nullable, Object defaultValue,
                     boolean autoIncrement, int ordinal) {
}
