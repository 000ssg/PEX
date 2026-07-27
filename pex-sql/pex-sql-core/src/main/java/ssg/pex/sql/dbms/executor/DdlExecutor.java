package ssg.pex.sql.dbms.executor;

import ssg.pex.result.Result;
import ssg.pex.sql.ast.*;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.dbms.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles CREATE/ALTER/DROP for tables, indexes, views.
 */
public class DdlExecutor {

    public Result<Void> executeCreateTable(CreateTableNode node, Schema schema) {
        try {
            if (schema.hasTable(node.tableName())) {
                if (node.ifNotExists()) {
                    return Result.success(null);
                }
                return Result.failure("SQL_ERROR", "Table already exists: " + node.tableName());
            }

            var columns = new ArrayList<Column>();
            var constraints = new ArrayList<TableConstraint>(node.constraints());

            for (int i = 0; i < node.columns().size(); i++) {
                ColumnDef def = node.columns().get(i);
                columns.add(new Column(def.name(), def.dataType(), def.nullable(),
                        def.defaultValue(), def.autoIncrement(), i));
                if (def.primaryKey()) {
                    constraints.add(new PrimaryKeyConstraint(List.of(def.name())));
                }
            }

            Table table = new Table(node.tableName(), columns, constraints);
            schema.addTable(table);
            return Result.success(null);
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }

    public Result<Void> executeDropTable(DropTableNode node, Schema schema) {
        try {
            if (!schema.hasTable(node.tableName())) {
                if (node.ifExists()) {
                    return Result.success(null);
                }
                return Result.failure("SQL_ERROR", "Table not found: " + node.tableName());
            }
            schema.removeTable(node.tableName());
            return Result.success(null);
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }

    public Result<Void> executeAlterTable(AlterTableNode node, Schema schema) {
        try {
            Table table = schema.getTable(node.tableName());
            if (table == null) {
                return Result.failure("SQL_ERROR", "Table not found: " + node.tableName());
            }

            for (AlterAction action : node.actions()) {
                switch (action) {
                    case AlterAction.AddColumn ac -> {
                        ColumnDef def = ac.column();
                        Column col = new Column(def.name(), def.dataType(), def.nullable(),
                                def.defaultValue(), def.autoIncrement(), table.columns().size());
                        table.addColumn(col);
                    }
                    case AlterAction.DropColumn dc -> {
                        table.dropColumn(dc.columnName());
                    }
                    case AlterAction.RenameColumn rc -> {
                        // Drop old and add new with same properties
                        Column old = table.getColumn(rc.oldName());
                        if (old == null) {
                            return Result.failure("SQL_ERROR", "Column not found: " + rc.oldName());
                        }
                        // Simple rename: drop and recreate would lose data, so update in-place
                        // For now, use drop/add approach (data in that column is lost)
                        // A proper implementation would rename in-place
                        table.dropColumn(rc.oldName());
                        table.addColumn(new Column(rc.newName(), old.dataType(), old.nullable(),
                                old.defaultValue(), old.autoIncrement(), table.columns().size()));
                    }
                    case AlterAction.AddConstraint ac -> {
                        table.addConstraint(ac.constraint());
                    }
                }
            }

            return Result.success(null);
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }

    public Result<Void> executeCreateIndex(CreateIndexNode node, Schema schema) {
        try {
            Table table = schema.getTable(node.tableName());
            if (table == null) {
                return Result.failure("SQL_ERROR", "Table not found: " + node.tableName());
            }

            Index index = new Index(node.indexName(), node.tableName(), node.columns(), node.unique());
            schema.addIndex(index);
            return Result.success(null);
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }

    public Result<Void> executeCreateView(CreateViewNode node, Schema schema) {
        try {
            if (!node.orReplace() && schema.getView(node.viewName()) != null) {
                return Result.failure("SQL_ERROR", "View already exists: " + node.viewName());
            }

            // Extract column names from the select items
            List<String> columnNames = node.query().selectItems().stream()
                    .map(item -> item.alias() != null ? item.alias() : item.expression())
                    .toList();

            View view = new View(node.viewName(), node.query(), columnNames);
            schema.addView(view);
            return Result.success(null);
        } catch (Exception e) {
            return Result.failure("SQL_ERROR", e.getMessage(), e);
        }
    }
}
