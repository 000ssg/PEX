package ssg.pex.sql.dbms;

import ssg.pex.sql.ast.SelectNode;

import java.util.List;

public record View(String name, SelectNode definition, List<String> columnNames) {
}
