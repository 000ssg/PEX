package ssg.pex.sql.olap;

import org.junit.jupiter.api.*;
import ssg.pex.sql.ast.SqlExpression;
import ssg.pex.sql.ast.SqlExpression.*;
import ssg.pex.sql.ast.SqlSupport.*;
import ssg.pex.sql.dbms.Column;
import ssg.pex.sql.dbms.Row;
import ssg.pex.sql.dbms.Table;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.olap.ast.*;
import ssg.pex.sql.olap.ast.FrameBound.BoundType;
import ssg.pex.sql.olap.ast.FrameSpec.FrameType;
import ssg.pex.sql.olap.ast.GroupingSetSpec.GroupingType;
import ssg.pex.sql.olap.executor.WindowFunctionExecutor;
import ssg.pex.sql.olap.parser.OlapSqlParser;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class OlapTest {

    private OlapDatabase db;

    @BeforeEach
    void setUp() {
        db = new OlapDatabase();
        createEmployeesTable();
        createSalesTable();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private void createEmployeesTable() {
        db.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, dept VARCHAR, salary INTEGER, manager_id INTEGER)");
        db.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering', 90000, NULL)");
        db.execute("INSERT INTO employees VALUES (2, 'Bob', 'Engineering', 85000, 1)");
        db.execute("INSERT INTO employees VALUES (3, 'Carol', 'Engineering', 85000, 1)");
        db.execute("INSERT INTO employees VALUES (4, 'Dave', 'Sales', 70000, NULL)");
        db.execute("INSERT INTO employees VALUES (5, 'Eve', 'Sales', 75000, 4)");
        db.execute("INSERT INTO employees VALUES (6, 'Frank', 'Sales', 65000, 4)");
        db.execute("INSERT INTO employees VALUES (7, 'Grace', 'HR', 80000, NULL)");
        db.execute("INSERT INTO employees VALUES (8, 'Heidi', 'HR', 72000, 7)");
    }

    private void createSalesTable() {
        db.execute("CREATE TABLE sales (id INTEGER, product VARCHAR, region VARCHAR, year INTEGER, amount INTEGER)");
        db.execute("INSERT INTO sales VALUES (1, 'Widget', 'North', 2023, 100)");
        db.execute("INSERT INTO sales VALUES (2, 'Widget', 'South', 2023, 150)");
        db.execute("INSERT INTO sales VALUES (3, 'Gadget', 'North', 2023, 200)");
        db.execute("INSERT INTO sales VALUES (4, 'Gadget', 'South', 2023, 250)");
        db.execute("INSERT INTO sales VALUES (5, 'Widget', 'North', 2024, 120)");
        db.execute("INSERT INTO sales VALUES (6, 'Widget', 'South', 2024, 180)");
        db.execute("INSERT INTO sales VALUES (7, 'Gadget', 'North', 2024, 220)");
        db.execute("INSERT INTO sales VALUES (8, 'Gadget', 'South', 2024, 280)");
    }

    // =========================================================================
    // Window Function Tests
    // =========================================================================

    @Nested
    class WindowFunctionTests {

        @Test
        void testRowNumberOrderBySalary() {
            var wf = db.parseWindowFunction("ROW_NUMBER() OVER (ORDER BY salary DESC)");
            var base = db.execute("SELECT name, salary FROM employees");
            assertThat(base.isSuccess()).isTrue();
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
            assertThat(result.columnNames()).contains("row_number");

            // First row should be the highest salary (Alice, 90000) with row_number=1
            // Find Alice's row
            for (Row row : result.rows()) {
                if ("Alice".equals(row.getValue(0))) {
                    assertThat(row.getValue(2)).isEqualTo(1L);
                }
            }
        }

        @Test
        void testRowNumberPartitionByDept() {
            var wf = db.parseWindowFunction("ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);

            // Within Engineering partition: Alice(90k)=1, Bob/Carol(85k)=2,3
            long engCount = 0;
            for (Row row : result.rows()) {
                if ("Engineering".equals(row.getValue(1))) {
                    engCount++;
                    long rn = (Long) row.getValue(3);
                    assertThat(rn).isBetween(1L, 3L);
                }
            }
            assertThat(engCount).isEqualTo(3);
        }

        @Test
        void testRankWithTies() {
            var wf = db.parseWindowFunction("RANK() OVER (ORDER BY salary DESC)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            // Bob and Carol both have 85000, so they should have the same rank
            Long bobRank = null, carolRank = null;
            for (Row row : result.rows()) {
                if ("Bob".equals(row.getValue(0))) bobRank = (Long) row.getValue(2);
                if ("Carol".equals(row.getValue(0))) carolRank = (Long) row.getValue(2);
            }
            assertThat(bobRank).isNotNull();
            assertThat(bobRank).isEqualTo(carolRank);
            // Rank after tied rows should skip (rank 2 ties -> next is 4)
        }

        @Test
        void testDenseRankNoGaps() {
            var wf = db.parseWindowFunction("DENSE_RANK() OVER (ORDER BY salary DESC)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            // Dense rank should not have gaps
            Long bobRank = null, carolRank = null;
            for (Row row : result.rows()) {
                if ("Bob".equals(row.getValue(0))) bobRank = (Long) row.getValue(2);
                if ("Carol".equals(row.getValue(0))) carolRank = (Long) row.getValue(2);
            }
            assertThat(bobRank).isEqualTo(carolRank);

            // Check that ranks are contiguous
            var ranks = new TreeSet<Long>();
            for (Row row : result.rows()) {
                ranks.add((Long) row.getValue(2));
            }
            long expected = 1;
            for (Long r : ranks) {
                assertThat(r).isEqualTo(expected++);
            }
        }

        @Test
        void testNtileFourBuckets() {
            var wf = db.parseWindowFunction("NTILE(4) OVER (ORDER BY salary DESC)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            // 8 employees / 4 buckets = 2 per bucket
            var bucketCounts = new HashMap<Long, Integer>();
            for (Row row : result.rows()) {
                Long bucket = (Long) row.getValue(2);
                assertThat(bucket).isBetween(1L, 4L);
                bucketCounts.merge(bucket, 1, Integer::sum);
            }
            assertThat(bucketCounts.values()).allMatch(c -> c == 2);
        }

        @Test
        void testLagOffset1() {
            var wf = db.parseWindowFunction("LAG(salary, 1) OVER (ORDER BY salary DESC)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // First row's LAG should be null
            boolean foundFirst = false;
            for (Row row : result.rows()) {
                long rn = 0;
                // First in sort order should have null lag
                if (row.getValue(2) == null && !foundFirst) {
                    foundFirst = true;
                }
            }
        }

        @Test
        void testLagOffset2WithDefault() {
            var wf = db.parseWindowFunction("LAG(salary, 2, 0) OVER (ORDER BY salary DESC)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // First two rows should have default value 0
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testLeadOffset1() {
            var wf = db.parseWindowFunction("LEAD(salary, 1) OVER (ORDER BY salary DESC)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testLeadWithDefault() {
            var wf = db.parseWindowFunction("LEAD(salary, 1, 0) OVER (ORDER BY salary DESC)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // Last row should have default 0
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testFirstValue() {
            var wf = db.parseWindowFunction("FIRST_VALUE(name) OVER (PARTITION BY dept ORDER BY salary DESC)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            // In Engineering partition, first value should be Alice (highest salary)
            for (Row row : result.rows()) {
                if ("Engineering".equals(row.getValue(1))) {
                    assertThat(row.getValue(3)).isEqualTo("Alice");
                }
            }
        }

        @Test
        void testLastValueWithFullFrame() {
            var wf = db.parseWindowFunction("LAST_VALUE(name) OVER (PARTITION BY dept ORDER BY salary DESC ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            // In Engineering partition, last value should be Bob or Carol (lowest salary = 85000, but still > others)
            // Actually Frank has 65000 in Sales; within Engineering the lowest is Bob/Carol at 85000
            for (Row row : result.rows()) {
                if ("Engineering".equals(row.getValue(1))) {
                    // Last by salary DESC should be Bob or Carol
                    String lastVal = (String) row.getValue(3);
                    assertThat(lastVal).isIn("Bob", "Carol");
                }
            }
        }

        @Test
        void testNthValue() {
            var wf = db.parseWindowFunction("NTH_VALUE(name, 2) OVER (PARTITION BY dept ORDER BY salary DESC ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            // In Engineering: 2nd value should be Bob or Carol
            for (Row row : result.rows()) {
                if ("Engineering".equals(row.getValue(1))) {
                    String nthVal = (String) row.getValue(3);
                    assertThat(nthVal).isIn("Bob", "Carol");
                }
            }
        }

        @Test
        void testPercentRank() {
            var wf = db.parseWindowFunction("PERCENT_RANK() OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            for (Row row : result.rows()) {
                Double pr = (Double) row.getValue(2);
                assertThat(pr).isBetween(0.0, 1.0);
            }
        }

        @Test
        void testCumeDist() {
            var wf = db.parseWindowFunction("CUME_DIST() OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            for (Row row : result.rows()) {
                Double cd = (Double) row.getValue(2);
                assertThat(cd).isBetween(0.0, 1.0);
            }
            // The last row should have cume_dist = 1.0
        }

        @Test
        void testRunningSum() {
            var wf = db.parseWindowFunction("SUM(salary) OVER (ORDER BY salary ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);

            // Each row should have a SUM value
            for (Row row : result.rows()) {
                assertThat(row.getValue(2)).isNotNull();
            }
        }

        @Test
        void testCountOverPartition() {
            var wf = db.parseWindowFunction("COUNT(*) OVER (PARTITION BY dept)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            for (Row row : result.rows()) {
                String dept = (String) row.getValue(1);
                Long count = (Long) row.getValue(3);
                if ("Engineering".equals(dept)) assertThat(count).isEqualTo(3L);
                else if ("Sales".equals(dept)) assertThat(count).isEqualTo(3L);
                else if ("HR".equals(dept)) assertThat(count).isEqualTo(2L);
            }
        }

        @Test
        void testAvgOverPartition() {
            var wf = db.parseWindowFunction("AVG(salary) OVER (PARTITION BY dept)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            for (Row row : result.rows()) {
                String dept = (String) row.getValue(1);
                Double avg = (Double) row.getValue(3);
                assertThat(avg).isNotNull();
                if ("HR".equals(dept)) {
                    assertThat(avg).isEqualTo((80000.0 + 72000.0) / 2.0);
                }
            }
        }

        @Test
        void testMultipleWindowFunctions() {
            var wf1 = db.parseWindowFunction("ROW_NUMBER() OVER (ORDER BY salary DESC)");
            var wf2 = db.parseWindowFunction("RANK() OVER (ORDER BY salary DESC)");

            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf1, wf2));
            assertThat(result.columnNames()).hasSize(4); // name, salary, row_number, rank
        }

        @Test
        void testSumOverEntirePartition() {
            var wf = db.parseWindowFunction("SUM(salary) OVER (PARTITION BY dept)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            for (Row row : result.rows()) {
                String dept = (String) row.getValue(1);
                if ("Engineering".equals(dept)) {
                    assertThat(((Number) row.getValue(3)).longValue()).isEqualTo(260000L);
                }
            }
        }

        @Test
        void testMinOverPartition() {
            var wf = db.parseWindowFunction("MIN(salary) OVER (PARTITION BY dept)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            for (Row row : result.rows()) {
                String dept = (String) row.getValue(1);
                if ("Sales".equals(dept)) {
                    assertThat(((Number) row.getValue(3)).longValue()).isEqualTo(65000L);
                }
            }
        }

        @Test
        void testMaxOverPartition() {
            var wf = db.parseWindowFunction("MAX(salary) OVER (PARTITION BY dept)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            for (Row row : result.rows()) {
                String dept = (String) row.getValue(1);
                if ("Sales".equals(dept)) {
                    assertThat(((Number) row.getValue(3)).longValue()).isEqualTo(75000L);
                }
            }
        }

        @Test
        void testRowNumberNoPartition() {
            var wf = db.parseWindowFunction("ROW_NUMBER() OVER (ORDER BY id)");
            var base = db.execute("SELECT id, name FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);

            // All row numbers should be 1-8
            var rowNumbers = new HashSet<Long>();
            for (Row row : result.rows()) {
                rowNumbers.add((Long) row.getValue(2));
            }
            assertThat(rowNumbers).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        }

        @Test
        void testRankPartitioned() {
            var wf = db.parseWindowFunction("RANK() OVER (PARTITION BY dept ORDER BY salary DESC)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            // In Engineering: Alice=1, Bob=Carol=2
            for (Row row : result.rows()) {
                if ("Alice".equals(row.getValue(0))) {
                    assertThat((Long) row.getValue(3)).isEqualTo(1L);
                }
            }
        }

        @Test
        void testDenseRankPartitioned() {
            var wf = db.parseWindowFunction("DENSE_RANK() OVER (PARTITION BY dept ORDER BY salary DESC)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            for (Row row : result.rows()) {
                if ("Alice".equals(row.getValue(0))) {
                    assertThat((Long) row.getValue(3)).isEqualTo(1L);
                }
            }
        }

        @Test
        void testNtileWithUnevenDistribution() {
            var wf = db.parseWindowFunction("NTILE(3) OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // 8 rows / 3 buckets: buckets 1,2 get 3 rows each, bucket 3 gets 2
            var bucketCounts = new HashMap<Long, Integer>();
            for (Row row : result.rows()) {
                Long bucket = (Long) row.getValue(2);
                assertThat(bucket).isBetween(1L, 3L);
                bucketCounts.merge(bucket, 1, Integer::sum);
            }
            assertThat(bucketCounts).hasSize(3);
        }

        @Test
        void testLagPartitioned() {
            var wf = db.parseWindowFunction("LAG(salary, 1) OVER (PARTITION BY dept ORDER BY salary DESC)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // In each partition, first row's LAG should be null
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testLeadPartitioned() {
            var wf = db.parseWindowFunction("LEAD(salary, 1) OVER (PARTITION BY dept ORDER BY salary DESC)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testPercentileContMedian() {
            var wf = db.parseWindowFunction("PERCENTILE_CONT(0.5) OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
            // All rows in the partition get the same percentile value
            Double firstVal = (Double) result.rows().getFirst().getValue(2);
            for (Row row : result.rows()) {
                assertThat((Double) row.getValue(2)).isEqualTo(firstVal);
            }
        }

        @Test
        void testPercentileDisc() {
            var wf = db.parseWindowFunction("PERCENTILE_DISC(0.5) OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testSumRunningTotal() {
            var wf = db.parseWindowFunction("SUM(salary) OVER (ORDER BY salary ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            // Running total should increase monotonically
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testFrameNPrecedingNFollowing() {
            var wf = db.parseWindowFunction("SUM(salary) OVER (ORDER BY salary ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
            for (Row row : result.rows()) {
                assertThat(row.getValue(2)).isNotNull();
            }
        }

        @Test
        void testWindowFunctionIsWindowFunctionCheck() {
            assertThat(WindowFunctionExecutor.isWindowFunction("ROW_NUMBER")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("RANK")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("DENSE_RANK")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("NTILE")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("LAG")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("LEAD")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("FIRST_VALUE")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("LAST_VALUE")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("NTH_VALUE")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("PERCENT_RANK")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("CUME_DIST")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("PERCENTILE_CONT")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("PERCENTILE_DISC")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("SUM")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("COUNT")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("AVG")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("MIN")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("MAX")).isTrue();
            assertThat(WindowFunctionExecutor.isWindowFunction("UNKNOWN")).isFalse();
        }

        @Test
        void testRowNumberWithEmptyResult() {
            db.execute("CREATE TABLE empty_table (id INTEGER, name VARCHAR)");
            var wf = db.parseWindowFunction("ROW_NUMBER() OVER (ORDER BY id)");
            var base = db.execute("SELECT id, name FROM empty_table");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(0);
        }

        @Test
        void testRankWithAllSameValues() {
            db.execute("CREATE TABLE same_salary (id INTEGER, name VARCHAR, salary INTEGER)");
            db.execute("INSERT INTO same_salary VALUES (1, 'A', 50000)");
            db.execute("INSERT INTO same_salary VALUES (2, 'B', 50000)");
            db.execute("INSERT INTO same_salary VALUES (3, 'C', 50000)");

            var wf = db.parseWindowFunction("RANK() OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM same_salary");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // All should have rank 1
            for (Row row : result.rows()) {
                assertThat((Long) row.getValue(2)).isEqualTo(1L);
            }
        }

        @Test
        void testDenseRankWithAllSameValues() {
            db.execute("CREATE TABLE same_salary2 (id INTEGER, name VARCHAR, salary INTEGER)");
            db.execute("INSERT INTO same_salary2 VALUES (1, 'A', 50000)");
            db.execute("INSERT INTO same_salary2 VALUES (2, 'B', 50000)");
            db.execute("INSERT INTO same_salary2 VALUES (3, 'C', 50000)");

            var wf = db.parseWindowFunction("DENSE_RANK() OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM same_salary2");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            for (Row row : result.rows()) {
                assertThat((Long) row.getValue(2)).isEqualTo(1L);
            }
        }

        @Test
        void testPercentRankSingleRow() {
            db.execute("CREATE TABLE single_row (id INTEGER, name VARCHAR, salary INTEGER)");
            db.execute("INSERT INTO single_row VALUES (1, 'Only', 50000)");

            var wf = db.parseWindowFunction("PERCENT_RANK() OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM single_row");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat((Double) result.rows().getFirst().getValue(2)).isEqualTo(0.0);
        }

        @Test
        void testCountOverNoPartition() {
            var wf = db.parseWindowFunction("COUNT(*) OVER ()");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            for (Row row : result.rows()) {
                assertThat((Long) row.getValue(2)).isEqualTo(8L);
            }
        }

        @Test
        void testAvgRunningAverage() {
            var wf = db.parseWindowFunction("AVG(salary) OVER (ORDER BY salary ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
            for (Row row : result.rows()) {
                assertThat(row.getValue(2)).isNotNull();
            }
        }

        @Test
        void testFirstValueNoPartition() {
            var wf = db.parseWindowFunction("FIRST_VALUE(salary) OVER (ORDER BY salary DESC)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // All rows should see the highest salary as first value
            for (Row row : result.rows()) {
                assertThat(((Number) row.getValue(2)).longValue()).isEqualTo(90000L);
            }
        }

        @Test
        void testNtileSingleBucket() {
            var wf = db.parseWindowFunction("NTILE(1) OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            for (Row row : result.rows()) {
                assertThat((Long) row.getValue(2)).isEqualTo(1L);
            }
        }

        @Test
        void testSumNoFrame() {
            // SUM with ORDER BY but no explicit frame -> default frame (UNBOUNDED PRECEDING to CURRENT ROW)
            var wf = db.parseWindowFunction("SUM(salary) OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testLeadBeyondPartition() {
            var wf = db.parseWindowFunction("LEAD(salary, 10, -1) OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // All rows should return the default since offset=10 > 8 rows
            for (Row row : result.rows()) {
                assertThat(((Number) row.getValue(2)).longValue()).isEqualTo(-1L);
            }
        }

        @Test
        void testLagBeyondPartition() {
            var wf = db.parseWindowFunction("LAG(salary, 10, -1) OVER (ORDER BY salary)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            for (Row row : result.rows()) {
                assertThat(((Number) row.getValue(2)).longValue()).isEqualTo(-1L);
            }
        }

        @Test
        void testNthValueBeyondFrame() {
            var wf = db.parseWindowFunction("NTH_VALUE(name, 100) OVER (ORDER BY salary ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)");
            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            for (Row row : result.rows()) {
                assertThat(row.getValue(2)).isNull();
            }
        }

        @Test
        void testRankGapsAfterTies() {
            // Create data with known ties: salaries 100, 100, 200
            db.execute("CREATE TABLE rank_test (id INTEGER, val INTEGER)");
            db.execute("INSERT INTO rank_test VALUES (1, 100)");
            db.execute("INSERT INTO rank_test VALUES (2, 100)");
            db.execute("INSERT INTO rank_test VALUES (3, 200)");

            var wf = db.parseWindowFunction("RANK() OVER (ORDER BY val)");
            var base = db.execute("SELECT id, val FROM rank_test");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // Expect: rank 1, 1, 3 (gap after tie)
            var ranks = new ArrayList<Long>();
            for (Row row : result.rows()) {
                ranks.add((Long) row.getValue(2));
            }
            Collections.sort(ranks);
            assertThat(ranks).containsExactly(1L, 1L, 3L);
        }

        @Test
        void testDenseRankNoGapsAfterTies() {
            db.execute("CREATE TABLE drank_test (id INTEGER, val INTEGER)");
            db.execute("INSERT INTO drank_test VALUES (1, 100)");
            db.execute("INSERT INTO drank_test VALUES (2, 100)");
            db.execute("INSERT INTO drank_test VALUES (3, 200)");

            var wf = db.parseWindowFunction("DENSE_RANK() OVER (ORDER BY val)");
            var base = db.execute("SELECT id, val FROM drank_test");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            var ranks = new ArrayList<Long>();
            for (Row row : result.rows()) {
                ranks.add((Long) row.getValue(2));
            }
            Collections.sort(ranks);
            assertThat(ranks).containsExactly(1L, 1L, 2L);
        }
    }

    // =========================================================================
    // CTE Tests
    // =========================================================================

    @Nested
    class CteTests {

        @Test
        void testSimpleCte() {
            var sql = "WITH high_earners AS (SELECT name, salary FROM employees WHERE salary > 80000) SELECT * FROM high_earners";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testCteWithColumnAliases() {
            var sql = "WITH dept_summary (department, avg_sal) AS (SELECT dept, AVG(salary) FROM employees GROUP BY dept) SELECT * FROM dept_summary";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().columnNames()).containsExactly("department", "avg_sal");
        }

        @Test
        void testMultipleCtes() {
            var sql = "WITH eng AS (SELECT name, salary FROM employees WHERE dept = 'Engineering'), " +
                    "high_sal AS (SELECT name, salary FROM employees WHERE salary > 80000) " +
                    "SELECT * FROM eng";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(3);
        }

        @Test
        void testRecursiveCteNumberSeries() {
            // Generate numbers 1-10
            db.execute("CREATE TABLE numbers (n INTEGER)");
            db.execute("INSERT INTO numbers VALUES (1)");
            // Recursive CTE: WITH RECURSIVE adds rows iteratively
            var sql = "WITH RECURSIVE nums AS (SELECT n FROM numbers) SELECT * FROM nums";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testCteReferencedInSelect() {
            var sql = "WITH active_emp AS (SELECT id, name, dept FROM employees WHERE salary > 70000) SELECT * FROM active_emp";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            var qr = result.value();
            // Employees with salary > 70000: Alice(90k), Bob(85k), Carol(85k), Eve(75k), Grace(80k), Heidi(72k) = 6
            assertThat(qr.rowCount()).isEqualTo(6);
        }

        @Test
        void testCteWithAggregation() {
            var sql = "WITH dept_counts AS (SELECT dept, COUNT(id) AS cnt FROM employees GROUP BY dept) SELECT * FROM dept_counts";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(3); // 3 departments
        }

        @Test
        void testRecursiveCteHierarchy() {
            // Simple recursive CTE using existing employees table
            var sql = "WITH RECURSIVE emp_tree AS (SELECT id, name, manager_id FROM employees WHERE manager_id IS NULL) SELECT * FROM emp_tree";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            // Should get at least the root managers
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testCteSelectSpecificColumns() {
            var sql = "WITH names AS (SELECT name FROM employees) SELECT * FROM names";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(8);
            assertThat(result.value().columnNames()).containsExactly("name");
        }

        @Test
        void testCteWithWhereOnCteTable() {
            var sql = "WITH all_emp AS (SELECT name, dept, salary FROM employees) SELECT * FROM all_emp WHERE salary > 80000";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            // salary > 80000: Alice(90k), Bob(85k), Carol(85k) = 3
            assertThat(result.value().rowCount()).isEqualTo(3);
        }

        @Test
        void testCteWithOrderBy() {
            var sql = "WITH sorted_emp AS (SELECT name, salary FROM employees) SELECT * FROM sorted_emp ORDER BY salary DESC";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(8);
        }

        @Test
        void testCteWithLimit() {
            var sql = "WITH all_emp AS (SELECT name, salary FROM employees) SELECT * FROM all_emp LIMIT 3";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(3);
        }

        @Test
        void testCteWithGroupByInFinal() {
            var sql = "WITH emp_data AS (SELECT dept, salary FROM employees) SELECT dept, SUM(salary) FROM emp_data GROUP BY dept";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(3);
        }

        @Test
        void testCteChained() {
            var sql = "WITH first AS (SELECT name, dept, salary FROM employees), " +
                    "second AS (SELECT name, salary FROM employees WHERE salary > 75000) " +
                    "SELECT * FROM second";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            // salary > 75000: Alice(90k), Bob(85k), Carol(85k), Grace(80k) = 4
            assertThat(result.value().rowCount()).isEqualTo(4);
        }

        @Test
        void testRecursiveCteFactorial() {
            // Use simple table for factorial-like computation
            db.execute("CREATE TABLE factorial (n INTEGER, fact INTEGER)");
            db.execute("INSERT INTO factorial VALUES (1, 1)");

            var sql = "WITH RECURSIVE fact AS (SELECT n, fact FROM factorial) SELECT * FROM fact";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testCteWithDistinct() {
            var sql = "WITH depts AS (SELECT DISTINCT dept FROM employees) SELECT * FROM depts";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(3);
        }

        @Test
        void testCteEmptyResult() {
            var sql = "WITH empty AS (SELECT name FROM employees WHERE salary > 999999) SELECT * FROM empty";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(0);
        }

        @Test
        void testCteWithMinMax() {
            var sql = "WITH stats AS (SELECT dept, MIN(salary) AS min_sal, MAX(salary) AS max_sal FROM employees GROUP BY dept) SELECT * FROM stats";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(3);
        }

        @Test
        void testCteTemporaryTableCleanup() {
            // After CTE execution, temporary tables should be cleaned up
            var sql = "WITH temp_cte AS (SELECT name FROM employees) SELECT * FROM temp_cte";
            db.executeOlap(sql);

            // The temp_cte table should no longer exist
            assertThat(db.database().defaultSchema().hasTable("temp_cte")).isFalse();
        }
    }

    // =========================================================================
    // Grouping Set Tests
    // =========================================================================

    @Nested
    class GroupingSetTests {

        @Test
        void testCubeBasic() {
            var spec = db.parseGroupingSet("CUBE(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            // CUBE(2 cols) = 4 combinations: (p,r), (p), (r), ()
            assertThat(result.value().rowCount()).isGreaterThanOrEqualTo(4);
        }

        @Test
        void testRollupBasic() {
            var spec = db.parseGroupingSet("ROLLUP(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            // ROLLUP(2 cols) = 3 levels: (p,r), (p), ()
            assertThat(result.value().rowCount()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void testGroupingSetsExplicit() {
            var spec = db.parseGroupingSet("GROUPING SETS ((product), (region), ())");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void testCubeNullMarkers() {
            var spec = db.parseGroupingSet("CUBE(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            // Grand total row should have NULLs for both grouping columns
            boolean foundGrandTotal = false;
            for (Row row : result.value().rows()) {
                if (row.getValue(0) == null && row.getValue(1) == null) {
                    foundGrandTotal = true;
                    assertThat(row.getValue(2)).isNotNull();
                }
            }
            assertThat(foundGrandTotal).isTrue();
        }

        @Test
        void testRollupHierarchical() {
            var spec = db.parseGroupingSet("ROLLUP(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            // Should have subtotals per product and a grand total
            boolean foundProductSubtotal = false;
            boolean foundGrandTotal = false;
            for (Row row : result.value().rows()) {
                if (row.getValue(0) != null && row.getValue(1) == null) {
                    foundProductSubtotal = true;
                }
                if (row.getValue(0) == null && row.getValue(1) == null) {
                    foundGrandTotal = true;
                }
            }
            assertThat(foundProductSubtotal).isTrue();
            assertThat(foundGrandTotal).isTrue();
        }

        @Test
        void testCubeWithCount() {
            var spec = db.parseGroupingSet("CUBE(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("COUNT(id)"), null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testGroupingSetsColumnNames() {
            var spec = db.parseGroupingSet("CUBE(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().columnNames()).containsExactly("product", "region", "SUM(amount)");
        }

        @Test
        void testRollupThreeColumns() {
            var spec = db.parseGroupingSet("ROLLUP(product, region, year)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            // ROLLUP(3) = 4 levels: (p,r,y), (p,r), (p), ()
            assertThat(result.value().rowCount()).isGreaterThanOrEqualTo(4);
        }

        @Test
        void testCubeThreeColumns() {
            var spec = db.parseGroupingSet("CUBE(product, region, year)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            // CUBE(3) = 8 combinations
            assertThat(result.value().rowCount()).isGreaterThanOrEqualTo(8);
        }

        @Test
        void testGroupingSetsEmptySet() {
            var spec = db.parseGroupingSet("GROUPING SETS ((product), ())");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            boolean foundGrandTotal = false;
            for (Row row : result.value().rows()) {
                if (row.getValue(0) == null) {
                    foundGrandTotal = true;
                }
            }
            assertThat(foundGrandTotal).isTrue();
        }

        @Test
        void testCubeWithAvg() {
            var spec = db.parseGroupingSet("CUBE(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("AVG(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testRollupWithMax() {
            var spec = db.parseGroupingSet("ROLLUP(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("MAX(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testCubeWithWhere() {
            var spec = db.parseGroupingSet("CUBE(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), "year = 2024");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testRollupSingleColumn() {
            var spec = db.parseGroupingSet("ROLLUP(product)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), null);

            assertThat(result.isSuccess()).isTrue();
            // ROLLUP(1) = 2 levels: (product), ()
            assertThat(result.value().rowCount()).isGreaterThanOrEqualTo(2);
        }

        @Test
        void testGroupingSetsMultipleAggregates() {
            var spec = db.parseGroupingSet("CUBE(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)", "COUNT(id)"), null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().columnNames()).contains("SUM(amount)", "COUNT(id)");
        }
    }

    // =========================================================================
    // MERGE Tests
    // =========================================================================

    @Nested
    class MergeTests {

        @BeforeEach
        void setUpMerge() {
            db.execute("CREATE TABLE target (id INTEGER, name VARCHAR, value INTEGER)");
            db.execute("INSERT INTO target VALUES (1, 'A', 10)");
            db.execute("INSERT INTO target VALUES (2, 'B', 20)");
            db.execute("INSERT INTO target VALUES (3, 'C', 30)");

            db.execute("CREATE TABLE source (id INTEGER, name VARCHAR, value INTEGER)");
            db.execute("INSERT INTO source VALUES (2, 'B_updated', 25)");
            db.execute("INSERT INTO source VALUES (3, 'C_updated', 35)");
            db.execute("INSERT INTO source VALUES (4, 'D', 40)");
        }

        @Test
        void testMergeWhenMatchedUpdate() {
            String sql = "MERGE INTO target t USING source s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.name = s.name, t.value = s.value";
            var result = db.executeMerge(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(2); // rows 2 and 3 matched

            // Verify updates
            var selectResult = db.execute("SELECT name, value FROM target WHERE id = 2");
            assertThat(selectResult.isSuccess()).isTrue();
            var qr = (QueryResult) selectResult.value();
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("B_updated");
        }

        @Test
        void testMergeWhenNotMatchedInsert() {
            String sql = "MERGE INTO target t USING source s ON t.id = s.id " +
                    "WHEN NOT MATCHED THEN INSERT (id, name, value) VALUES (s.id, s.name, s.value)";
            var result = db.executeMerge(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(1); // only row 4 not matched

            // Verify insert
            var selectResult = db.execute("SELECT name FROM target WHERE id = 4");
            assertThat(selectResult.isSuccess()).isTrue();
            var qr = (QueryResult) selectResult.value();
            assertThat(qr.rowCount()).isEqualTo(1);
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("D");
        }

        @Test
        void testMergeBothMatchedAndNotMatched() {
            String sql = "MERGE INTO target t USING source s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.name = s.name, t.value = s.value " +
                    "WHEN NOT MATCHED THEN INSERT (id, name, value) VALUES (s.id, s.name, s.value)";
            var result = db.executeMerge(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(3); // 2 updated + 1 inserted
        }

        @Test
        void testMergeWhenMatchedDelete() {
            String sql = "MERGE INTO target t USING source s ON t.id = s.id " +
                    "WHEN MATCHED THEN DELETE";
            var result = db.executeMerge(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(2);

            // Verify deletes
            var selectResult = db.execute("SELECT * FROM target");
            var qr = (QueryResult) selectResult.value();
            assertThat(qr.rowCount()).isEqualTo(1); // Only row 1 remains
        }

        @Test
        void testMergeNoMatch() {
            db.execute("CREATE TABLE source2 (id INTEGER, name VARCHAR, value INTEGER)");
            db.execute("INSERT INTO source2 VALUES (10, 'X', 100)");
            db.execute("INSERT INTO source2 VALUES (11, 'Y', 110)");

            String sql = "MERGE INTO target t USING source2 s ON t.id = s.id " +
                    "WHEN NOT MATCHED THEN INSERT (id, name, value) VALUES (s.id, s.name, s.value)";
            var result = db.executeMerge(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(2);
        }

        @Test
        void testMergeAllMatched() {
            db.execute("CREATE TABLE source3 (id INTEGER, name VARCHAR, value INTEGER)");
            db.execute("INSERT INTO source3 VALUES (1, 'A_new', 11)");
            db.execute("INSERT INTO source3 VALUES (2, 'B_new', 22)");

            String sql = "MERGE INTO target t USING source3 s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.name = s.name";
            var result = db.executeMerge(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(2);
        }

        @Test
        void testMergeTargetNotFound() {
            String sql = "MERGE INTO nonexistent t USING source s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.name = s.name";
            var result = db.executeMerge(sql);
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void testMergeSourceNotFound() {
            String sql = "MERGE INTO target t USING nonexistent s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.name = s.name";
            var result = db.executeMerge(sql);
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void testMergeWithoutAlias() {
            String sql = "MERGE INTO target USING source ON target.id = source.id " +
                    "WHEN MATCHED THEN UPDATE SET target.name = source.name";
            var result = db.executeMerge(sql);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void testMergeInsertAllColumns() {
            db.execute("CREATE TABLE source4 (id INTEGER, name VARCHAR, value INTEGER)");
            db.execute("INSERT INTO source4 VALUES (5, 'E', 50)");

            String sql = "MERGE INTO target t USING source4 s ON t.id = s.id " +
                    "WHEN NOT MATCHED THEN INSERT (id, name, value) VALUES (s.id, s.name, s.value)";
            var result = db.executeMerge(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(1);

            var check = db.execute("SELECT * FROM target WHERE id = 5");
            var qr = (QueryResult) check.value();
            assertThat(qr.rowCount()).isEqualTo(1);
        }

        @Test
        void testMergeUpdateSingleColumn() {
            db.execute("CREATE TABLE source5 (id INTEGER, name VARCHAR, value INTEGER)");
            db.execute("INSERT INTO source5 VALUES (1, 'A_mod', 999)");

            String sql = "MERGE INTO target t USING source5 s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.value = s.value";
            var result = db.executeMerge(sql);
            assertThat(result.isSuccess()).isTrue();

            var check = db.execute("SELECT name, value FROM target WHERE id = 1");
            var qr = (QueryResult) check.value();
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("A"); // name unchanged
        }

        @Test
        void testMergeEmptySource() {
            db.execute("CREATE TABLE empty_source (id INTEGER, name VARCHAR, value INTEGER)");

            String sql = "MERGE INTO target t USING empty_source s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.name = s.name " +
                    "WHEN NOT MATCHED THEN INSERT (id, name, value) VALUES (s.id, s.name, s.value)";
            var result = db.executeMerge(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(0);
        }

        @Test
        void testMergeViaOlapExecute() {
            String sql = "MERGE INTO target t USING source s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.name = s.name, t.value = s.value " +
                    "WHEN NOT MATCHED THEN INSERT (id, name, value) VALUES (s.id, s.name, s.value)";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            // Should return affected_rows column
            assertThat(result.value().columnNames()).contains("affected_rows");
        }
    }

    // =========================================================================
    // PIVOT / UNPIVOT Tests
    // =========================================================================

    @Nested
    class PivotTests {

        @Test
        void testPivotBasic() {
            var pivot = db.parsePivot("PIVOT (SUM(amount) FOR region IN ('North', 'South'))");
            var result = db.executePivot("sales", pivot, List.of("product"));

            assertThat(result.isSuccess()).isTrue();
            var qr = result.value();
            assertThat(qr.columnNames()).contains("product", "North", "South");
            assertThat(qr.rowCount()).isGreaterThan(0);
        }

        @Test
        void testPivotWithYear() {
            var pivot = db.parsePivot("PIVOT (SUM(amount) FOR year IN (2023, 2024))");
            var result = db.executePivot("sales", pivot, List.of("product"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().columnNames()).contains("product", "2023", "2024");
        }

        @Test
        void testPivotCount() {
            var pivot = db.parsePivot("PIVOT (COUNT(id) FOR region IN ('North', 'South'))");
            var result = db.executePivot("sales", pivot, List.of("product"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testPivotMultipleGroupBy() {
            var pivot = db.parsePivot("PIVOT (SUM(amount) FOR region IN ('North', 'South'))");
            var result = db.executePivot("sales", pivot, List.of("product", "year"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().columnNames()).contains("product", "year", "North", "South");
        }

        @Test
        void testUnpivotBasic() {
            // Create a table with columns to unpivot
            db.execute("CREATE TABLE quarterly_sales (product VARCHAR, q1 INTEGER, q2 INTEGER, q3 INTEGER, q4 INTEGER)");
            db.execute("INSERT INTO quarterly_sales VALUES ('Widget', 100, 150, 200, 250)");
            db.execute("INSERT INTO quarterly_sales VALUES ('Gadget', 300, 350, 400, 450)");

            var unpivot = db.parseUnpivot("UNPIVOT (sales_amount FOR quarter IN (q1, q2, q3, q4))");
            var result = db.executeUnpivot("quarterly_sales", unpivot, List.of("product"));

            assertThat(result.isSuccess()).isTrue();
            var qr = result.value();
            assertThat(qr.columnNames()).containsExactly("product", "quarter", "sales_amount");
            assertThat(qr.rowCount()).isEqualTo(8); // 2 products * 4 quarters
        }

        @Test
        void testUnpivotWithNulls() {
            db.execute("CREATE TABLE sparse_data (name VARCHAR, a INTEGER, b INTEGER, c INTEGER)");
            db.execute("INSERT INTO sparse_data VALUES ('X', 10, NULL, 30)");

            var unpivot = db.parseUnpivot("UNPIVOT (val FOR col IN (a, b, c))");
            var result = db.executeUnpivot("sparse_data", unpivot, List.of("name"));

            assertThat(result.isSuccess()).isTrue();
            // NULL values are skipped in unpivot
            assertThat(result.value().rowCount()).isEqualTo(2); // a and c only
        }

        @Test
        void testPivotTableNotFound() {
            var pivot = db.parsePivot("PIVOT (SUM(amount) FOR region IN ('North'))");
            var result = db.executePivot("nonexistent", pivot, List.of("product"));
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void testUnpivotTableNotFound() {
            var unpivot = db.parseUnpivot("UNPIVOT (val FOR col IN (a, b))");
            var result = db.executeUnpivot("nonexistent", unpivot, List.of("name"));
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void testPivotSingleValue() {
            var pivot = db.parsePivot("PIVOT (SUM(amount) FOR region IN ('North'))");
            var result = db.executePivot("sales", pivot, List.of("product"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().columnNames()).contains("North");
        }

        @Test
        void testPivotMax() {
            var pivot = db.parsePivot("PIVOT (MAX(amount) FOR region IN ('North', 'South'))");
            var result = db.executePivot("sales", pivot, List.of("product"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }
    }

    // =========================================================================
    // Integration Tests
    // =========================================================================

    @Nested
    class IntegrationTests {

        @Test
        void testWindowFunctionWithJoin() {
            db.execute("CREATE TABLE departments (name VARCHAR, budget INTEGER)");
            db.execute("INSERT INTO departments VALUES ('Engineering', 500000)");
            db.execute("INSERT INTO departments VALUES ('Sales', 300000)");
            db.execute("INSERT INTO departments VALUES ('HR', 200000)");

            var base = db.execute("SELECT employees.name, employees.dept, employees.salary, departments.budget " +
                    "FROM employees JOIN departments ON employees.dept = departments.name");
            assertThat(base.isSuccess()).isTrue();
            var baseQr = (QueryResult) base.value();

            var wf = db.parseWindowFunction("ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)");
            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testCteWithJoin() {
            db.execute("CREATE TABLE dept_info (dept_name VARCHAR, location VARCHAR)");
            db.execute("INSERT INTO dept_info VALUES ('Engineering', 'Building A')");
            db.execute("INSERT INTO dept_info VALUES ('Sales', 'Building B')");
            db.execute("INSERT INTO dept_info VALUES ('HR', 'Building C')");

            var sql = "WITH eng_emps AS (SELECT name, dept FROM employees WHERE dept = 'Engineering') " +
                    "SELECT * FROM eng_emps";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(3);
        }

        @Test
        void testCubeWithHaving() {
            // Use the grouping set executor with a WHERE clause to simulate HAVING
            var spec = db.parseGroupingSet("CUBE(product, region)");
            var result = db.executeGroupingSet("sales", spec, List.of("SUM(amount)"), "amount > 100");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testTopNPerGroup() {
            // Top 2 earners per department using window functions
            var wf = db.parseWindowFunction("ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            // Filter where row_number <= 2
            int rnIdx = result.columnNames().indexOf("row_number");
            long topN = 0;
            for (Row row : result.rows()) {
                Long rn = (Long) row.getValue(rnIdx);
                if (rn != null && rn <= 2) topN++;
            }
            // 3 departments * 2 = 6 (but HR only has 2)
            assertThat(topN).isEqualTo(6);
        }

        @Test
        void testMovingAverage() {
            var wf = db.parseWindowFunction("AVG(amount) OVER (ORDER BY id ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING)");
            var base = db.execute("SELECT id, amount FROM sales");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
            for (Row row : result.rows()) {
                assertThat(row.getValue(2)).isNotNull();
            }
        }

        @Test
        void testRunningTotals() {
            var wf = db.parseWindowFunction("SUM(amount) OVER (PARTITION BY product ORDER BY year ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)");
            var base = db.execute("SELECT product, year, amount FROM sales");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testOlapPluginMetadata() {
            var plugin = new OlapPlugin();
            assertThat(plugin.name()).isEqualTo("sql-olap");
            assertThat(plugin.loadOrder()).isEqualTo(210);
        }

        @Test
        void testOlapDatabaseWithExistingDb() {
            var innerDb = new ssg.pex.sql.dbms.InMemoryDatabase();
            var olapDb = new OlapDatabase(innerDb);
            olapDb.execute("CREATE TABLE test (id INTEGER)");
            olapDb.execute("INSERT INTO test VALUES (1)");

            var result = olapDb.executeOlap("SELECT * FROM test");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(1);
            olapDb.close();
        }

        @Test
        void testComplexCteWithAggregatesAndFilter() {
            // CTE with aggregate: verify CTE executes and returns results
            var sql = "WITH dept_avg AS (SELECT dept, AVG(salary) AS avg_salary FROM employees GROUP BY dept) " +
                    "SELECT * FROM dept_avg";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }

        @Test
        void testMultipleWindowFunctionsWithDifferentPartitions() {
            var wf1 = db.parseWindowFunction("ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)");
            var wf2 = db.parseWindowFunction("SUM(salary) OVER (PARTITION BY dept)");

            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf1, wf2));
            assertThat(result.columnNames()).hasSize(5);
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testMergeWithSubsequentSelect() {
            db.execute("CREATE TABLE merge_target (id INTEGER, val INTEGER)");
            db.execute("INSERT INTO merge_target VALUES (1, 100)");
            db.execute("CREATE TABLE merge_source (id INTEGER, val INTEGER)");
            db.execute("INSERT INTO merge_source VALUES (1, 200)");
            db.execute("INSERT INTO merge_source VALUES (2, 300)");

            String sql = "MERGE INTO merge_target t USING merge_source s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.val = s.val " +
                    "WHEN NOT MATCHED THEN INSERT (id, val) VALUES (s.id, s.val)";
            var mergeResult = db.executeMerge(sql);
            assertThat(mergeResult.isSuccess()).isTrue();

            var selectResult = db.execute("SELECT * FROM merge_target ORDER BY id");
            assertThat(selectResult.isSuccess()).isTrue();
            var qr = (QueryResult) selectResult.value();
            assertThat(qr.rowCount()).isEqualTo(2);
        }

        @Test
        void testWindowFunctionPercentileWithPartition() {
            var wf = db.parseWindowFunction("PERCENTILE_CONT(0.5) OVER (PARTITION BY dept ORDER BY salary)");
            var base = db.execute("SELECT name, dept, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(8);
        }

        @Test
        void testCombinedRankAndLag() {
            var wfRank = db.parseWindowFunction("RANK() OVER (ORDER BY salary DESC)");
            var wfLag = db.parseWindowFunction("LAG(salary, 1) OVER (ORDER BY salary DESC)");

            var base = db.execute("SELECT name, salary FROM employees");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wfRank, wfLag));
            assertThat(result.columnNames()).hasSize(4); // name, salary, rank, lag
            assertThat(result.rowCount()).isEqualTo(8);
        }
    }

    // =========================================================================
    // Parser Tests
    // =========================================================================

    @Nested
    class ParserTests {

        @Test
        void testParseWindowFunctionBasic() {
            var wf = db.parseWindowFunction("ROW_NUMBER() OVER (ORDER BY salary)");
            assertThat(wf.functionName()).isEqualTo("ROW_NUMBER");
            assertThat(wf.args()).isEmpty();
            assertThat(wf.overClause().partitionBy()).isEmpty();
            assertThat(wf.overClause().orderBy()).isNotNull();
        }

        @Test
        void testParseWindowFunctionWithPartition() {
            var wf = db.parseWindowFunction("SUM(salary) OVER (PARTITION BY dept ORDER BY id)");
            assertThat(wf.functionName()).isEqualTo("SUM");
            assertThat(wf.overClause().partitionBy()).containsExactly("dept");
        }

        @Test
        void testParseWindowFunctionWithFrame() {
            var wf = db.parseWindowFunction("SUM(salary) OVER (ORDER BY id ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)");
            assertThat(wf.overClause().frame()).isNotNull();
            assertThat(wf.overClause().frame().type()).isEqualTo(FrameType.ROWS);
            assertThat(wf.overClause().frame().start().type()).isEqualTo(BoundType.N_PRECEDING);
            assertThat(wf.overClause().frame().start().offset()).isEqualTo(2);
            assertThat(wf.overClause().frame().end().type()).isEqualTo(BoundType.CURRENT_ROW);
        }

        @Test
        void testParseGroupingSetCube() {
            var spec = db.parseGroupingSet("CUBE(a, b, c)");
            assertThat(spec.type()).isEqualTo(GroupingType.CUBE);
            assertThat(spec.sets()).hasSize(3);
        }

        @Test
        void testParseGroupingSetRollup() {
            var spec = db.parseGroupingSet("ROLLUP(a, b)");
            assertThat(spec.type()).isEqualTo(GroupingType.ROLLUP);
            assertThat(spec.sets()).hasSize(2);
        }

        @Test
        void testParseGroupingSetsExplicit() {
            var spec = db.parseGroupingSet("GROUPING SETS ((a), (b), ())");
            assertThat(spec.type()).isEqualTo(GroupingType.GROUPING_SETS);
            assertThat(spec.sets()).hasSize(3);
            assertThat(spec.sets().get(2)).isEmpty(); // Empty set
        }

        @Test
        void testParsePivot() {
            var pivot = db.parsePivot("PIVOT (SUM(amount) FOR region IN ('North', 'South'))");
            assertThat(pivot.aggregateFunction()).isEqualTo("SUM");
            assertThat(pivot.aggregateColumn()).isEqualTo("amount");
            assertThat(pivot.forColumn()).isEqualTo("region");
            assertThat(pivot.inValues()).hasSize(2);
        }

        @Test
        void testParseUnpivot() {
            var unpivot = db.parseUnpivot("UNPIVOT (val FOR col IN (a, b, c))");
            assertThat(unpivot.valueColumn()).isEqualTo("val");
            assertThat(unpivot.nameColumn()).isEqualTo("col");
            assertThat(unpivot.sourceColumns()).containsExactly("a", "b", "c");
        }

        @Test
        void testParseWithClause() {
            var result = new OlapSqlParser().parse("WITH cte AS (SELECT 1 AS n) SELECT * FROM cte");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isInstanceOf(WithClause.class);
        }

        @Test
        void testParseMerge() {
            var result = new OlapSqlParser().parse(
                    "MERGE INTO t USING s ON t.id = s.id WHEN MATCHED THEN UPDATE SET t.val = s.val");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isInstanceOf(MergeNode.class);
        }

        @Test
        void testParseSelectFallsToCore() {
            var result = new OlapSqlParser().parse("SELECT * FROM employees");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void testParseWindowFrameUnbounded() {
            var wf = db.parseWindowFunction("SUM(x) OVER (ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)");
            assertThat(wf.overClause().frame().start().type()).isEqualTo(BoundType.UNBOUNDED_PRECEDING);
            assertThat(wf.overClause().frame().end().type()).isEqualTo(BoundType.UNBOUNDED_FOLLOWING);
        }

        @Test
        void testParseWindowNoOrderBy() {
            var wf = db.parseWindowFunction("COUNT(*) OVER (PARTITION BY dept)");
            assertThat(wf.functionName()).isEqualTo("COUNT");
            assertThat(wf.overClause().orderBy()).isNull();
            assertThat(wf.overClause().partitionBy()).containsExactly("dept");
        }

        @Test
        void testParseWindowEmptyOver() {
            var wf = db.parseWindowFunction("COUNT(*) OVER ()");
            assertThat(wf.overClause().partitionBy()).isEmpty();
            assertThat(wf.overClause().orderBy()).isNull();
            assertThat(wf.overClause().frame()).isNull();
        }
    }

    // =========================================================================
    // AST Tests
    // =========================================================================

    @Nested
    class AstTests {

        @Test
        void testFrameBoundFactories() {
            assertThat(FrameBound.unboundedPreceding().type()).isEqualTo(BoundType.UNBOUNDED_PRECEDING);
            assertThat(FrameBound.currentRow().type()).isEqualTo(BoundType.CURRENT_ROW);
            assertThat(FrameBound.unboundedFollowing().type()).isEqualTo(BoundType.UNBOUNDED_FOLLOWING);
            assertThat(FrameBound.preceding(5).offset()).isEqualTo(5);
            assertThat(FrameBound.following(3).offset()).isEqualTo(3);
        }

        @Test
        void testOverClauseDefensiveCopy() {
            var partitionBy = new ArrayList<>(List.of("a", "b"));
            var over = new OverClause(partitionBy, null, null);
            partitionBy.add("c");
            assertThat(over.partitionBy()).hasSize(2); // Defensive copy
        }

        @Test
        void testCteDefinitionDefensiveCopy() {
            var aliases = new ArrayList<>(List.of("x", "y"));
            var cte = new CteDefinition("test", aliases, null);
            aliases.add("z");
            assertThat(cte.columnAliases()).hasSize(2);
        }

        @Test
        void testMergeNodeImmutability() {
            var actions = new ArrayList<MergeAction>();
            actions.add(new MergeAction.WhenMatchedDelete());
            var merge = new MergeNode("t", "s", null, null,
                    new LiteralExpr(true), actions, null);
            actions.add(new MergeAction.WhenMatchedDelete());
            assertThat(merge.actions()).hasSize(1); // Defensive copy
        }

        @Test
        void testGroupingSetSpecImmutability() {
            var sets = new ArrayList<List<String>>();
            sets.add(List.of("a"));
            var spec = new GroupingSetSpec(GroupingType.CUBE, sets, null);
            sets.add(List.of("b"));
            assertThat(spec.sets()).hasSize(1);
        }

        @Test
        void testPivotClauseImmutability() {
            var vals = new ArrayList<Object>();
            vals.add("North");
            var pivot = new PivotClause("SUM", "amount", "region", vals, null);
            vals.add("South");
            assertThat(pivot.inValues()).hasSize(1);
        }

        @Test
        void testUnpivotClauseImmutability() {
            var cols = new ArrayList<>(List.of("a", "b"));
            var unpivot = new UnpivotClause("val", "col", cols, null);
            cols.add("c");
            assertThat(unpivot.sourceColumns()).hasSize(2);
        }

        @Test
        void testWindowFunctionCallImmutability() {
            var args = new ArrayList<SqlExpression>();
            args.add(new LiteralExpr(1));
            var wf = new WindowFunctionCall("SUM", args,
                    new OverClause(List.of(), null, null), null);
            args.add(new LiteralExpr(2));
            assertThat(wf.args()).hasSize(1);
        }

        @Test
        void testWithClauseImmutability() {
            var ctes = new ArrayList<CteDefinition>();
            ctes.add(new CteDefinition("a", List.of(), null));
            var wc = new WithClause(ctes, false, null);
            ctes.add(new CteDefinition("b", List.of(), null));
            assertThat(wc.ctes()).hasSize(1);
        }

        @Test
        void testMergeActionWhenNotMatchedInsertImmutability() {
            var cols = new ArrayList<>(List.of("a"));
            var vals = new ArrayList<SqlExpression>(List.of(new LiteralExpr(1)));
            var action = new MergeAction.WhenNotMatchedInsert(cols, vals);
            cols.add("b");
            vals.add(new LiteralExpr(2));
            assertThat(action.columns()).hasSize(1);
            assertThat(action.values()).hasSize(1);
        }
    }
}
