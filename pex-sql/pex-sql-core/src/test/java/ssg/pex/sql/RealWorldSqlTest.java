package ssg.pex.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-world, complex SQL test scenarios that exercise InMemoryDatabase end-to-end
 * with multi-table JOINs, complex WHERE clauses, subqueries, DML, transactions,
 * stored procedures, and views.
 */
class RealWorldSqlTest {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() {
        db = new InMemoryDatabase();
    }

    // ---- Helpers ----

    private QueryResult query(String sql) {
        var result = db.execute(sql);
        assertThat(result.isSuccess())
                .as("Expected success for: %s, but got: %s", sql, result.isFailure() ? result.error() : "")
                .isTrue();
        return (QueryResult) result.value();
    }

    private DmlResult dml(String sql) {
        var result = db.execute(sql);
        assertThat(result.isSuccess())
                .as("Expected success for: %s, but got: %s", sql, result.isFailure() ? result.error() : "")
                .isTrue();
        return (DmlResult) result.value();
    }

    private void exec(String sql) {
        var result = db.execute(sql);
        assertThat(result.isSuccess())
                .as("Expected success for: %s, but got: %s", sql, result.isFailure() ? result.error() : "")
                .isTrue();
    }

    // ---- Schema helpers ----

    private void createHrSchema() {
        exec("CREATE TABLE departments (id INTEGER PRIMARY KEY, dept_name VARCHAR(100), budget DOUBLE, location VARCHAR(100))");
        exec("CREATE TABLE employees (id INTEGER PRIMARY KEY, name VARCHAR(100), dept_id INTEGER, salary DOUBLE, manager_id INTEGER, hire_date VARCHAR(10))");
        exec("CREATE TABLE projects (id INTEGER PRIMARY KEY, project_name VARCHAR(100), lead_id INTEGER, dept_id INTEGER, active BOOLEAN)");

        dml("INSERT INTO departments (id, dept_name, budget, location) VALUES (1, 'Engineering', 500000, 'San Francisco')");
        dml("INSERT INTO departments (id, dept_name, budget, location) VALUES (2, 'Marketing', 300000, 'New York')");
        dml("INSERT INTO departments (id, dept_name, budget, location) VALUES (3, 'Finance', 250000, 'Chicago')");
        dml("INSERT INTO departments (id, dept_name, budget, location) VALUES (4, 'HR', 150000, 'Boston')");
        dml("INSERT INTO departments (id, dept_name, budget, location) VALUES (5, 'Research', 400000, 'Seattle')");

        dml("INSERT INTO employees (id, name, dept_id, salary, manager_id, hire_date) VALUES (1, 'Alice', 1, 130000.0, NULL, '2018-01-15')");
        dml("INSERT INTO employees (id, name, dept_id, salary, manager_id, hire_date) VALUES (2, 'Bob', 1, 95000.0, 1, '2019-03-01')");
        dml("INSERT INTO employees (id, name, dept_id, salary, manager_id, hire_date) VALUES (3, 'Charlie', 2, 88000.0, 1, '2020-06-10')");
        dml("INSERT INTO employees (id, name, dept_id, salary, manager_id, hire_date) VALUES (4, 'Diana', 2, 76000.0, 3, '2021-01-20')");
        dml("INSERT INTO employees (id, name, dept_id, salary, manager_id, hire_date) VALUES (5, 'Eve', 3, 115000.0, 1, '2018-09-05')");
        dml("INSERT INTO employees (id, name, dept_id, salary, manager_id, hire_date) VALUES (6, 'Frank', 1, 105000.0, 1, '2019-11-12')");
        dml("INSERT INTO employees (id, name, dept_id, salary, manager_id, hire_date) VALUES (7, 'Grace', 3, 92000.0, 5, '2020-02-28')");
        dml("INSERT INTO employees (id, name, dept_id, salary, manager_id, hire_date) VALUES (8, 'Hank', 2, 72000.0, 3, '2022-04-15')");
        dml("INSERT INTO employees (id, name, dept_id, salary, manager_id, hire_date) VALUES (9, 'Ivy', 5, 110000.0, NULL, '2017-07-01')");
        dml("INSERT INTO employees (id, name, dept_id, salary, manager_id, hire_date) VALUES (10, 'Jake', 5, 98000.0, 9, '2019-08-22')");

        dml("INSERT INTO projects (id, project_name, lead_id, dept_id, active) VALUES (1, 'Project Alpha', 1, 1, TRUE)");
        dml("INSERT INTO projects (id, project_name, lead_id, dept_id, active) VALUES (2, 'Project Beta', 3, 2, TRUE)");
        dml("INSERT INTO projects (id, project_name, lead_id, dept_id, active) VALUES (3, 'Project Gamma', 5, 3, FALSE)");
        dml("INSERT INTO projects (id, project_name, lead_id, dept_id, active) VALUES (4, 'Project Delta', 9, 5, TRUE)");
        dml("INSERT INTO projects (id, project_name, lead_id, dept_id, active) VALUES (5, 'Project Epsilon', 2, 1, TRUE)");
    }

    // ==========================================================================
    // Multi-table JOINs
    // ==========================================================================

    @Nested
    @DisplayName("Multi-table JOINs")
    class MultiTableJoinTests {

        @Test
        @DisplayName("3-table JOIN via comma-separated FROM with aliases")
        void threeTableJoinWithAliases() {
            createHrSchema();
            var qr = query("SELECT e.name, d.dept_name, p.project_name FROM employees e, departments d, projects p WHERE e.dept_id = d.id AND e.id = p.lead_id");
            // Leads: Alice(1)->Alpha, Charlie(3)->Beta, Eve(5)->Gamma, Ivy(9)->Delta, Bob(2)->Epsilon
            assertThat(qr.rowCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("LEFT JOIN with NULL rows for unmatched department")
        void leftJoinWithNullRows() {
            createHrSchema();
            var qr = query("SELECT d.dept_name, e.name FROM departments d LEFT JOIN employees e ON d.id = e.dept_id");
            // 10 employees + HR dept(4) with no employees = 11
            assertThat(qr.rowCount()).isEqualTo(11);
            // Verify HR department has NULL employee row
            var hrNullRow = query("SELECT d.dept_name, e.name FROM departments d LEFT JOIN employees e ON d.id = e.dept_id WHERE e.name IS NULL");
            assertThat(hrNullRow.rowCount()).isEqualTo(1);
            assertThat(hrNullRow.rows().getFirst().getValue(0)).isEqualTo("HR");
        }

        @Test
        @DisplayName("Self-join: employee to manager relationship")
        void selfJoinEmployeeManager() {
            createHrSchema();
            // Find employees and their managers' names
            var qr = query("SELECT e1.name, e2.name FROM employees e1 JOIN employees e2 ON e1.manager_id = e2.id");
            // All employees except Alice and Ivy (who have no manager) = 8
            assertThat(qr.rowCount()).isEqualTo(8);
        }

        @Test
        @DisplayName("JOIN with aggregate GROUP BY department name")
        void joinWithAggregateGroupBy() {
            createHrSchema();
            var qr = query("SELECT d.dept_name, COUNT(*) FROM departments d JOIN employees e ON d.id = e.dept_id GROUP BY dept_name");
            // Engineering: 3, Marketing: 3, Finance: 2, Research: 2 = 4 groups (HR has no employees)
            assertThat(qr.rowCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("JOIN with WHERE, ORDER BY, LIMIT")
        void joinWithWhereOrderByLimit() {
            createHrSchema();
            var qr = query("SELECT e.name, e.salary FROM employees e JOIN departments d ON e.dept_id = d.id WHERE d.location = 'San Francisco' ORDER BY salary DESC LIMIT 2");
            // Engineering in SF: Alice 130k, Frank 105k, Bob 95k -> top 2
            assertThat(qr.rowCount()).isEqualTo(2);
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("Alice");
        }

        @Test
        @DisplayName("CROSS JOIN produces correct cartesian product")
        void crossJoinCartesianProduct() {
            exec("CREATE TABLE colors (name VARCHAR(20))");
            exec("CREATE TABLE sizes (name VARCHAR(10))");
            exec("CREATE TABLE patterns (name VARCHAR(20))");
            dml("INSERT INTO colors (name) VALUES ('Red')");
            dml("INSERT INTO colors (name) VALUES ('Blue')");
            dml("INSERT INTO sizes (name) VALUES ('S')");
            dml("INSERT INTO sizes (name) VALUES ('M')");
            dml("INSERT INTO sizes (name) VALUES ('L')");
            dml("INSERT INTO patterns (name) VALUES ('Solid')");
            dml("INSERT INTO patterns (name) VALUES ('Stripe')");
            var qr = query("SELECT * FROM colors CROSS JOIN sizes");
            assertThat(qr.rowCount()).isEqualTo(6); // 2 * 3
        }

        @Test
        @DisplayName("RIGHT JOIN shows all employees even without department match")
        void rightJoinShowsAllRightRows() {
            createHrSchema();
            var qr = query("SELECT * FROM departments d RIGHT JOIN employees e ON d.id = e.dept_id");
            assertThat(qr.rowCount()).isEqualTo(10);
        }
    }

    // ==========================================================================
    // Complex WHERE clauses
    // ==========================================================================

    @Nested
    @DisplayName("Complex WHERE clauses")
    class ComplexWhereTests {

        @Test
        @DisplayName("Nested AND/OR: (a > X AND b < Y) OR (c = Z AND d != W)")
        void nestedAndOr() {
            createHrSchema();
            // (dept_id = 1 AND salary > 100000) OR (dept_id = 3 AND salary > 90000)
            var qr = query("SELECT name FROM employees WHERE (dept_id = 1 AND salary > 100000) OR (dept_id = 3 AND salary > 90000)");
            // Eng > 100k: Alice(130k), Frank(105k); Finance > 90k: Eve(115k), Grace(92k)
            assertThat(qr.rowCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("BETWEEN with numbers filters correct range")
        void betweenWithNumbers() {
            createHrSchema();
            var qr = query("SELECT name FROM employees WHERE salary BETWEEN 90000 AND 110000");
            // Bob:95k, Eve: no(115k), Frank:105k, Grace:92k, Ivy:110k, Jake:98k = 5
            assertThat(qr.rowCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("NOT BETWEEN excludes the correct range")
        void notBetween() {
            createHrSchema();
            var qr = query("SELECT name FROM employees WHERE salary NOT BETWEEN 90000 AND 110000");
            // Alice:130k, Charlie:88k, Diana:76k, Eve:115k, Hank:72k = 5
            assertThat(qr.rowCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("IN with literal list filters correctly")
        void inWithLiteralList() {
            createHrSchema();
            var qr = query("SELECT * FROM employees WHERE dept_id IN (1, 5)");
            // Eng: Alice, Bob, Frank; Research: Ivy, Jake = 5
            assertThat(qr.rowCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("LIKE with various wildcard patterns")
        void likeWithWildcardPatterns() {
            createHrSchema();
            // Names starting with specific letters
            var qr1 = query("SELECT name FROM employees WHERE name LIKE 'A%'");
            assertThat(qr1.rowCount()).isEqualTo(1); // Alice

            var qr2 = query("SELECT name FROM employees WHERE name LIKE '%e'");
            assertThat(qr2.rowCount()).isEqualTo(5); // Alice, Charlie, Eve, Grace, Jake

            var qr3 = query("SELECT name FROM employees WHERE name LIKE '%an%'");
            assertThat(qr3.rowCount()).isEqualTo(3); // Diana, Frank, Hank
        }

        @Test
        @DisplayName("IS NULL and IS NOT NULL combined")
        void isNullAndIsNotNull() {
            createHrSchema();
            var qrNull = query("SELECT * FROM employees WHERE manager_id IS NULL");
            assertThat(qrNull.rowCount()).isEqualTo(2); // Alice, Ivy

            var qrNotNull = query("SELECT * FROM employees WHERE manager_id IS NOT NULL");
            assertThat(qrNotNull.rowCount()).isEqualTo(8);
        }

        @Test
        @DisplayName("CASE WHEN in WHERE with multiple branches")
        void caseWhenInWhere() {
            createHrSchema();
            var qr = query("SELECT name FROM employees WHERE CASE WHEN salary >= 110000 THEN 'Executive' WHEN salary >= 90000 THEN 'Senior' ELSE 'Junior' END = 'Senior'");
            // Senior: Bob(95k), Frank(105k), Grace(92k), Jake(98k)
            assertThat(qr.rowCount()).isEqualTo(4);
        }
    }

    // ==========================================================================
    // Subqueries (parsing + execution where supported)
    // ==========================================================================

    @Nested
    @DisplayName("Subqueries")
    class SubqueryTests {

        @Test
        @DisplayName("Scalar subquery in SELECT parses successfully")
        void scalarSubqueryInSelectParses() {
            createHrSchema();
            var parser = new ssg.pex.sql.grammar.SqlParser();
            var result = parser.parse("SELECT name, (SELECT COUNT(*) FROM projects WHERE projects.lead_id = employees.id) FROM employees");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Correlated subquery in WHERE parses successfully")
        void correlatedSubqueryInWhereParses() {
            createHrSchema();
            var parser = new ssg.pex.sql.grammar.SqlParser();
            var result = parser.parse("SELECT name FROM employees e WHERE salary > (SELECT AVG(salary) FROM employees WHERE dept_id = e.dept_id)");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Nested subqueries (2 levels) parse successfully")
        void nestedSubqueriesTwoLevels() {
            var parser = new ssg.pex.sql.grammar.SqlParser();
            var result = parser.parse("SELECT * FROM employees WHERE dept_id IN (SELECT id FROM departments WHERE id IN (SELECT dept_id FROM projects WHERE active = TRUE))");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("IN with subquery parses successfully")
        void inWithSubquery() {
            var parser = new ssg.pex.sql.grammar.SqlParser();
            var result = parser.parse("SELECT name FROM employees WHERE id IN (SELECT lead_id FROM projects WHERE active = TRUE)");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("EXISTS subquery parses successfully")
        void existsSubquery() {
            var parser = new ssg.pex.sql.grammar.SqlParser();
            var result = parser.parse("SELECT * FROM departments WHERE EXISTS (SELECT 1 FROM employees WHERE employees.dept_id = departments.id AND salary > 100000)");
            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ==========================================================================
    // DML with complexity
    // ==========================================================================

    @Nested
    @DisplayName("DML with complexity")
    class ComplexDmlTests {

        @Test
        @DisplayName("INSERT from SELECT copies rows to archive table")
        void insertFromSelect() {
            createHrSchema();
            exec("CREATE TABLE archive (id INTEGER, name VARCHAR(100), dept_id INTEGER, salary DOUBLE, manager_id INTEGER, hire_date VARCHAR(10))");
            dml("INSERT INTO archive SELECT * FROM employees WHERE salary > 100000");
            var qr = query("SELECT * FROM archive");
            // Alice:130k, Eve:115k, Frank:105k, Ivy:110k = 4
            assertThat(qr.rowCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("UPDATE with complex WHERE modifies correct rows")
        void updateWithComplexWhere() {
            createHrSchema();
            dml("UPDATE employees SET salary = 150000 WHERE dept_id = 1 AND salary > 100000");
            // Alice was 130k, Frank was 105k -> both updated
            var qr = query("SELECT * FROM employees WHERE salary = 150000");
            assertThat(qr.rowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("DELETE with complex WHERE removes correct subset")
        void deleteWithComplexWhere() {
            createHrSchema();
            dml("DELETE FROM employees WHERE dept_id = 2 AND salary < 80000");
            // Diana:76k, Hank:72k -> both deleted
            var qr = query("SELECT * FROM employees");
            assertThat(qr.rowCount()).isEqualTo(8);
        }

        @Test
        @DisplayName("Bulk operations: insert 20, update 5, delete 3, verify final state")
        void bulkOperations() {
            exec("CREATE TABLE inventory (id INTEGER PRIMARY KEY, item VARCHAR(50), qty INTEGER, status VARCHAR(20))");
            for (int i = 1; i <= 20; i++) {
                dml("INSERT INTO inventory (id, item, qty, status) VALUES (" + i + ", 'Item" + i + "', " + (i * 10) + ", 'active')");
            }
            assertThat(query("SELECT * FROM inventory").rowCount()).isEqualTo(20);

            // Update first 5 items to inactive
            dml("UPDATE inventory SET status = 'inactive' WHERE id <= 5");
            assertThat(query("SELECT * FROM inventory WHERE status = 'inactive'").rowCount()).isEqualTo(5);

            // Delete 3 items with lowest qty
            dml("DELETE FROM inventory WHERE qty <= 30");
            assertThat(query("SELECT * FROM inventory").rowCount()).isEqualTo(17);

            // Verify active count
            assertThat(query("SELECT * FROM inventory WHERE status = 'active'").rowCount()).isEqualTo(15);
            assertThat(query("SELECT * FROM inventory WHERE status = 'inactive'").rowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Multi-row INSERT and verify all rows")
        void multiRowInsert() {
            exec("CREATE TABLE batch_items (id INTEGER PRIMARY KEY, name VARCHAR(50), price DOUBLE)");
            var dr = dml("INSERT INTO batch_items (id, name, price) VALUES (1, 'A', 10.0), (2, 'B', 20.0), (3, 'C', 30.0), (4, 'D', 40.0), (5, 'E', 50.0), (6, 'F', 60.0), (7, 'G', 70.0)");
            assertThat(dr.affectedRows()).isEqualTo(7);
            assertThat(query("SELECT * FROM batch_items").rowCount()).isEqualTo(7);
        }
    }

    // ==========================================================================
    // Transactions
    // ==========================================================================

    @Nested
    @DisplayName("Transactions")
    class TransactionTests {

        @Test
        @DisplayName("BEGIN, INSERT, SELECT (verify rows), ROLLBACK, SELECT (verify rows gone)")
        void rollbackUndoesInsertsCompletely() {
            exec("CREATE TABLE txn_test (id INTEGER, value VARCHAR(100))");
            dml("INSERT INTO txn_test (id, value) VALUES (1, 'before')");

            exec("BEGIN");
            var txn = db.transactionManager().getTransaction("default");
            int rowsBefore = db.defaultSchema().getTable("txn_test").rowCount();
            dml("INSERT INTO txn_test (id, value) VALUES (2, 'in_txn_1')");
            txn.changeLog().recordInsert("txn_test", rowsBefore);
            dml("INSERT INTO txn_test (id, value) VALUES (3, 'in_txn_2')");
            txn.changeLog().recordInsert("txn_test", rowsBefore + 1);

            // Verify rows exist during transaction
            assertThat(query("SELECT * FROM txn_test").rowCount()).isEqualTo(3);

            exec("ROLLBACK");

            // After rollback, only the initial row remains
            var qr = query("SELECT * FROM txn_test");
            assertThat(qr.rowCount()).isEqualTo(1);
            assertThat(qr.rows().getFirst().getValue(1)).isEqualTo("before");
        }

        @Test
        @DisplayName("SAVEPOINT: BEGIN, INSERT, SAVEPOINT, INSERT, ROLLBACK TO SAVEPOINT, COMMIT")
        void savepointPartialRollback() {
            exec("CREATE TABLE sp_test (id INTEGER, value VARCHAR(100))");
            exec("BEGIN");
            var txn = db.transactionManager().getTransaction("default");

            dml("INSERT INTO sp_test (id, value) VALUES (1, 'first')");
            txn.changeLog().recordInsert("sp_test", 0);

            dml("INSERT INTO sp_test (id, value) VALUES (2, 'second')");
            txn.changeLog().recordInsert("sp_test", 1);

            exec("SAVEPOINT sp1");

            dml("INSERT INTO sp_test (id, value) VALUES (3, 'third')");
            txn.changeLog().recordInsert("sp_test", 2);

            dml("INSERT INTO sp_test (id, value) VALUES (4, 'fourth')");
            txn.changeLog().recordInsert("sp_test", 3);

            exec("ROLLBACK TO SAVEPOINT sp1");
            exec("COMMIT");

            // third and fourth undone, first and second remain
            var qr = query("SELECT * FROM sp_test");
            assertThat(qr.rowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Transaction with UPDATE then ROLLBACK restores original data")
        void transactionWithUpdateRollback() {
            exec("CREATE TABLE data (id INTEGER PRIMARY KEY, name VARCHAR(100), score INTEGER)");
            dml("INSERT INTO data (id, name, score) VALUES (1, 'Alice', 100)");
            dml("INSERT INTO data (id, name, score) VALUES (2, 'Bob', 200)");

            exec("BEGIN");
            var txn = db.transactionManager().getTransaction("default");
            var table = db.defaultSchema().getTable("data");
            var row0 = table.rows().get(0).copy();
            var row1 = table.rows().get(1).copy();
            dml("UPDATE data SET score = 999");
            txn.changeLog().recordUpdate("data", 0, row0);
            txn.changeLog().recordUpdate("data", 1, row1);

            // Verify update took effect
            assertThat(query("SELECT * FROM data WHERE score = 999").rowCount()).isEqualTo(2);

            exec("ROLLBACK");

            // Verify original values restored
            var qr = query("SELECT * FROM data ORDER BY id ASC");
            assertThat(qr.rowCount()).isEqualTo(2);
            assertThat(qr.rows().get(0).getValue(2)).isEqualTo(100L);
            assertThat(qr.rows().get(1).getValue(2)).isEqualTo(200L);
        }
    }

    // ==========================================================================
    // Stored Procedures
    // ==========================================================================

    @Nested
    @DisplayName("Stored Procedures")
    class StoredProcedureTests {

        @Test
        @DisplayName("CREATE PROCEDURE with IN/OUT params parses correctly")
        void createProcedureWithParams() {
            var parser = new ssg.pex.sql.grammar.SqlParser();
            var result = parser.parse("CREATE PROCEDURE calc_bonus(IN emp_id INTEGER, OUT bonus DOUBLE) BEGIN UPDATE employees SET salary = salary + 5000 WHERE id = 1; END");
            assertThat(result.isSuccess()).isTrue();
            var proc = (ssg.pex.sql.ast.CreateProcedureNode) result.value();
            assertThat(proc.procedureName()).isEqualTo("calc_bonus");
            assertThat(proc.params()).hasSize(2);
            assertThat(proc.params().get(0).mode()).isEqualTo(ssg.pex.sql.ast.SqlSupport.ParamMode.IN);
            assertThat(proc.params().get(1).mode()).isEqualTo(ssg.pex.sql.ast.SqlSupport.ParamMode.OUT);
        }

        @Test
        @DisplayName("Procedure modifies table data and changes are verified")
        void procedureModifiesTableData() {
            exec("CREATE TABLE accounts (id INTEGER PRIMARY KEY, balance DOUBLE)");
            dml("INSERT INTO accounts (id, balance) VALUES (1, 1000.0)");
            dml("INSERT INTO accounts (id, balance) VALUES (2, 2000.0)");

            exec("CREATE PROCEDURE reset_balances() BEGIN UPDATE accounts SET balance = 0; END");
            exec("CALL reset_balances()");

            var qr = query("SELECT * FROM accounts WHERE balance = 0");
            assertThat(qr.rowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Procedure with multiple statements modifies multiple tables")
        void procedureWithMultipleStatements() {
            exec("CREATE TABLE orders (id INTEGER PRIMARY KEY, status VARCHAR(20))");
            exec("CREATE TABLE audit_log (id INTEGER, action VARCHAR(100))");
            dml("INSERT INTO orders (id, status) VALUES (1, 'pending')");
            dml("INSERT INTO orders (id, status) VALUES (2, 'pending')");

            exec("CREATE PROCEDURE process_orders() BEGIN UPDATE orders SET status = 'processed'; INSERT INTO audit_log (id, action) VALUES (1, 'batch_process'); END");
            exec("CALL process_orders()");

            assertThat(query("SELECT * FROM orders WHERE status = 'processed'").rowCount()).isEqualTo(2);
            assertThat(query("SELECT * FROM audit_log").rowCount()).isEqualTo(1);
        }
    }

    // ==========================================================================
    // Views
    // ==========================================================================

    @Nested
    @DisplayName("Views")
    class ViewTests {

        @Test
        @DisplayName("CREATE VIEW and SELECT from view")
        void createViewAndSelect() {
            createHrSchema();
            exec("CREATE VIEW senior_employees AS SELECT * FROM employees WHERE salary > 100000");
            var qr = query("SELECT * FROM senior_employees");
            // Alice:130k, Eve:115k, Frank:105k, Ivy:110k = 4
            assertThat(qr.rowCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("View with JOIN provides filtered access")
        void viewWithJoin() {
            createHrSchema();
            // Create view selecting engineering employees via join
            exec("CREATE VIEW eng_team AS SELECT e.name, e.salary FROM employees e JOIN departments d ON e.dept_id = d.id WHERE d.dept_name = 'Engineering'");
            var qr = query("SELECT * FROM eng_team");
            // Alice, Bob, Frank in Engineering
            assertThat(qr.rowCount()).isEqualTo(3);
        }
    }

    // ==========================================================================
    // Complex aggregation and business queries
    // ==========================================================================

    @Nested
    @DisplayName("Complex aggregation")
    class ComplexAggregationTests {

        @Test
        @DisplayName("Multiple aggregates with GROUP BY on joined tables")
        void multipleAggregatesJoinGroupBy() {
            createHrSchema();
            var qr = query("SELECT d.dept_name, COUNT(*), SUM(e.salary), AVG(e.salary), MIN(e.salary), MAX(e.salary) FROM departments d JOIN employees e ON d.id = e.dept_id GROUP BY dept_name");
            assertThat(qr.rowCount()).isEqualTo(4); // 4 departments with employees
        }

        @Test
        @DisplayName("COUNT DISTINCT across JOIN")
        void countDistinctAcrossJoin() {
            createHrSchema();
            var qr = query("SELECT COUNT(DISTINCT dept_id) FROM employees");
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(4L); // depts 1,2,3,5
        }

        @Test
        @DisplayName("ORDER BY multiple columns with mixed direction")
        void orderByMultipleColumnsMixed() {
            createHrSchema();
            var qr = query("SELECT * FROM employees ORDER BY dept_id ASC, salary DESC");
            assertThat(qr.rowCount()).isEqualTo(10);
            // First row: dept 1, highest salary = Alice 130k
            assertThat(qr.rows().getFirst().getValue(1)).isEqualTo("Alice");
        }

        @Test
        @DisplayName("Aggregate on empty result returns correct values")
        void aggregateOnEmptyResult() {
            exec("CREATE TABLE empty_data (id INTEGER, value DOUBLE)");
            var qr = query("SELECT COUNT(*), SUM(value) FROM empty_data");
            assertThat(qr.rowCount()).isEqualTo(1);
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(0L);
        }

        @Test
        @DisplayName("GROUP BY with ORDER BY and LIMIT")
        void groupByWithOrderByAndLimit() {
            createHrSchema();
            var qr = query("SELECT dept_id, COUNT(*) FROM employees GROUP BY dept_id ORDER BY dept_id ASC LIMIT 2");
            assertThat(qr.rowCount()).isEqualTo(2);
        }
    }

    // ==========================================================================
    // Expression evaluation and edge cases
    // ==========================================================================

    @Nested
    @DisplayName("Expression evaluation edge cases")
    class ExpressionEdgeCaseTests {

        @Test
        @DisplayName("Arithmetic expressions in WHERE clause")
        void arithmeticInWhere() {
            exec("CREATE TABLE products (id INTEGER, price DOUBLE, qty INTEGER)");
            dml("INSERT INTO products (id, price, qty) VALUES (1, 10.0, 5)");
            dml("INSERT INTO products (id, price, qty) VALUES (2, 20.0, 3)");
            dml("INSERT INTO products (id, price, qty) VALUES (3, 5.0, 20)");
            var qr = query("SELECT * FROM products WHERE price * qty > 50");
            // Row 2: 20*3=60, Row 3: 5*20=100 => 2 rows
            assertThat(qr.rowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("String concatenation with || operator in WHERE")
        void stringConcatenationInWhere() {
            exec("CREATE TABLE people (id INTEGER, first_name VARCHAR(50), last_name VARCHAR(50))");
            dml("INSERT INTO people (id, first_name, last_name) VALUES (1, 'John', 'Doe')");
            dml("INSERT INTO people (id, first_name, last_name) VALUES (2, 'Jane', 'Smith')");
            var qr = query("SELECT * FROM people WHERE first_name || ' ' || last_name = 'John Doe'");
            assertThat(qr.rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("COALESCE function in WHERE")
        void coalesceInWhere() {
            exec("CREATE TABLE nullable (id INTEGER, val1 VARCHAR(50), val2 VARCHAR(50))");
            dml("INSERT INTO nullable (id, val1, val2) VALUES (1, NULL, 'backup')");
            dml("INSERT INTO nullable (id, val1, val2) VALUES (2, 'primary', 'backup')");
            dml("INSERT INTO nullable (id, val1, val2) VALUES (3, NULL, NULL)");
            var qr = query("SELECT * FROM nullable WHERE COALESCE(val1, val2) = 'backup'");
            assertThat(qr.rowCount()).isEqualTo(1); // Row 1: val1=NULL -> 'backup'
        }

        @Test
        @DisplayName("UPPER function in WHERE")
        void upperFunctionInWhere() {
            exec("CREATE TABLE words (id INTEGER, text VARCHAR(100))");
            dml("INSERT INTO words (id, text) VALUES (1, 'Hello World')");
            dml("INSERT INTO words (id, text) VALUES (2, 'goodbye')");
            var qr = query("SELECT * FROM words WHERE UPPER(text) = 'HELLO WORLD'");
            assertThat(qr.rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Boolean TRUE/FALSE in WHERE clause")
        void booleanValuesInWhere() {
            createHrSchema();
            var qr = query("SELECT * FROM projects WHERE active = TRUE");
            assertThat(qr.rowCount()).isEqualTo(4); // Alpha, Beta, Delta, Epsilon
        }

        @Test
        @DisplayName("Negative numbers in WHERE clause")
        void negativeNumbersInWhere() {
            exec("CREATE TABLE measurements (id INTEGER, value DOUBLE)");
            dml("INSERT INTO measurements (id, value) VALUES (1, -15.5)");
            dml("INSERT INTO measurements (id, value) VALUES (2, 10.0)");
            dml("INSERT INTO measurements (id, value) VALUES (3, -3.2)");
            dml("INSERT INTO measurements (id, value) VALUES (4, 0.0)");
            var qr = query("SELECT * FROM measurements WHERE value < 0");
            assertThat(qr.rowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("DISTINCT with multiple columns")
        void distinctMultipleColumns() {
            createHrSchema();
            var qr = query("SELECT DISTINCT dept_id, manager_id FROM employees WHERE manager_id IS NOT NULL");
            // Unique combos: (1,1)Bob/Frank, (2,1)Charlie, (2,3)Diana/Hank, (3,1)Eve, (3,5)Grace, (5,9)Jake = 6
            assertThat(qr.rowCount()).isEqualTo(6);
        }

        @Test
        @DisplayName("LIMIT with large OFFSET returns empty")
        void limitWithLargeOffset() {
            createHrSchema();
            var qr = query("SELECT * FROM employees LIMIT 10 OFFSET 100");
            assertThat(qr.rowCount()).isEqualTo(0);
        }
    }

    // ==========================================================================
    // DDL and Schema management
    // ==========================================================================

    @Nested
    @DisplayName("DDL and schema management")
    class DdlSchemaTests {

        @Test
        @DisplayName("ALTER TABLE ADD and DROP COLUMN")
        void alterTableAddAndDropColumn() {
            exec("CREATE TABLE evolving (id INTEGER PRIMARY KEY, name VARCHAR(100))");
            dml("INSERT INTO evolving (id, name) VALUES (1, 'Test')");
            exec("ALTER TABLE evolving ADD COLUMN status VARCHAR(20)");
            assertThat(db.defaultSchema().getTable("evolving").columns()).hasSize(3);
            exec("ALTER TABLE evolving DROP COLUMN status");
            assertThat(db.defaultSchema().getTable("evolving").columns()).hasSize(2);
        }

        @Test
        @DisplayName("Foreign key constraint prevents orphans")
        void foreignKeyConstraint() {
            exec("CREATE TABLE parent_tbl (id INTEGER PRIMARY KEY, name VARCHAR(50))");
            exec("CREATE TABLE child_tbl (id INTEGER PRIMARY KEY, parent_id INTEGER, FOREIGN KEY (parent_id) REFERENCES parent_tbl (id))");
            dml("INSERT INTO parent_tbl (id, name) VALUES (1, 'P1')");
            dml("INSERT INTO child_tbl (id, parent_id) VALUES (1, 1)");

            var orphanResult = db.execute("INSERT INTO child_tbl (id, parent_id) VALUES (2, 999)");
            assertThat(orphanResult.isFailure()).isTrue();

            var deleteResult = db.execute("DELETE FROM parent_tbl WHERE id = 1");
            assertThat(deleteResult.isFailure()).isTrue();
        }

        @Test
        @DisplayName("DROP TABLE and recreate with different schema")
        void dropAndRecreate() {
            exec("CREATE TABLE temp (id INTEGER, value VARCHAR(100))");
            dml("INSERT INTO temp (id, value) VALUES (1, 'old')");
            exec("DROP TABLE temp");
            exec("CREATE TABLE temp (id INTEGER, value VARCHAR(100), extra VARCHAR(50), created VARCHAR(20))");
            dml("INSERT INTO temp (id, value, extra, created) VALUES (1, 'new', 'additional', '2024-01-01')");
            var qr = query("SELECT * FROM temp");
            assertThat(qr.rowCount()).isEqualTo(1);
            assertThat(qr.columnNames()).hasSize(4);
        }
    }
}
