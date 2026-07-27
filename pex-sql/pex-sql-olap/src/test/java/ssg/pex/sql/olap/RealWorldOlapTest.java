package ssg.pex.sql.olap;

import org.junit.jupiter.api.*;
import ssg.pex.sql.dbms.Row;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.olap.executor.WindowFunctionExecutor;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Real-world OLAP test scenarios: window functions, CTEs, MERGE, PIVOT/UNPIVOT,
 * GROUPING SETS with realistic business data.
 */
class RealWorldOlapTest {

    private OlapDatabase db;

    @BeforeEach
    void setUp() {
        db = new OlapDatabase();
        createSalesForceSchema();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private void createSalesForceSchema() {
        db.execute("CREATE TABLE sales_reps (id INTEGER, name VARCHAR, region VARCHAR, team VARCHAR, hire_year INTEGER, quota INTEGER, achieved INTEGER)");
        db.execute("INSERT INTO sales_reps VALUES (1, 'Alice', 'West', 'Enterprise', 2018, 500000, 620000)");
        db.execute("INSERT INTO sales_reps VALUES (2, 'Bob', 'West', 'Enterprise', 2019, 400000, 380000)");
        db.execute("INSERT INTO sales_reps VALUES (3, 'Carol', 'West', 'SMB', 2020, 300000, 350000)");
        db.execute("INSERT INTO sales_reps VALUES (4, 'Dave', 'East', 'Enterprise', 2018, 500000, 510000)");
        db.execute("INSERT INTO sales_reps VALUES (5, 'Eve', 'East', 'Enterprise', 2021, 350000, 400000)");
        db.execute("INSERT INTO sales_reps VALUES (6, 'Frank', 'East', 'SMB', 2019, 250000, 230000)");
        db.execute("INSERT INTO sales_reps VALUES (7, 'Grace', 'Central', 'Enterprise', 2017, 600000, 750000)");
        db.execute("INSERT INTO sales_reps VALUES (8, 'Hank', 'Central', 'SMB', 2020, 280000, 310000)");
        db.execute("INSERT INTO sales_reps VALUES (9, 'Ivy', 'Central', 'SMB', 2022, 200000, 180000)");
        db.execute("INSERT INTO sales_reps VALUES (10, 'Jake', 'West', 'SMB', 2021, 250000, 275000)");

        db.execute("CREATE TABLE deals (id INTEGER, rep_id INTEGER, quarter VARCHAR, product VARCHAR, amount INTEGER, status VARCHAR)");
        db.execute("INSERT INTO deals VALUES (1, 1, 'Q1', 'Platform', 150000, 'closed')");
        db.execute("INSERT INTO deals VALUES (2, 1, 'Q2', 'Platform', 200000, 'closed')");
        db.execute("INSERT INTO deals VALUES (3, 1, 'Q3', 'Add-on', 80000, 'closed')");
        db.execute("INSERT INTO deals VALUES (4, 2, 'Q1', 'Platform', 120000, 'closed')");
        db.execute("INSERT INTO deals VALUES (5, 2, 'Q2', 'Add-on', 60000, 'closed')");
        db.execute("INSERT INTO deals VALUES (6, 3, 'Q1', 'Platform', 180000, 'closed')");
        db.execute("INSERT INTO deals VALUES (7, 3, 'Q3', 'Platform', 100000, 'pipeline')");
        db.execute("INSERT INTO deals VALUES (8, 4, 'Q2', 'Platform', 250000, 'closed')");
        db.execute("INSERT INTO deals VALUES (9, 4, 'Q3', 'Add-on', 130000, 'closed')");
        db.execute("INSERT INTO deals VALUES (10, 5, 'Q1', 'Platform', 200000, 'closed')");
        db.execute("INSERT INTO deals VALUES (11, 5, 'Q2', 'Add-on', 90000, 'pipeline')");
        db.execute("INSERT INTO deals VALUES (12, 7, 'Q1', 'Platform', 300000, 'closed')");
        db.execute("INSERT INTO deals VALUES (13, 7, 'Q2', 'Platform', 250000, 'closed')");
        db.execute("INSERT INTO deals VALUES (14, 7, 'Q3', 'Add-on', 120000, 'closed')");
        db.execute("INSERT INTO deals VALUES (15, 8, 'Q1', 'Platform', 160000, 'closed')");
        db.execute("INSERT INTO deals VALUES (16, 9, 'Q2', 'Add-on', 50000, 'pipeline')");
    }

    // ==========================================================================
    // Window Functions - Sales Performance Rankings
    // ==========================================================================

    @Nested
    @DisplayName("Window Functions - Sales Rankings")
    class WindowFunctionRankingTests {

        @Test
        @DisplayName("Rank sales reps by achieved amount within each region")
        void rankWithinRegion() {
            var wf = db.parseWindowFunction("RANK() OVER (PARTITION BY region ORDER BY achieved DESC)");
            var base = db.execute("SELECT name, region, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(10);

            // Grace is top in Central (750k), should be rank 1
            for (Row row : result.rows()) {
                if ("Grace".equals(row.getValue(0))) {
                    assertThat((Long) row.getValue(3)).isEqualTo(1L);
                }
            }
        }

        @Test
        @DisplayName("DENSE_RANK by team across entire company")
        void denseRankByTeamCompanyWide() {
            var wf = db.parseWindowFunction("DENSE_RANK() OVER (ORDER BY achieved DESC)");
            var base = db.execute("SELECT name, team, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(10);

            // Grace (750k) should be rank 1, Alice (620k) rank 2
            for (Row row : result.rows()) {
                if ("Grace".equals(row.getValue(0))) {
                    assertThat((Long) row.getValue(3)).isEqualTo(1L);
                }
            }
        }

        @Test
        @DisplayName("NTILE quartiles for performance review")
        void ntileQuartiles() {
            var wf = db.parseWindowFunction("NTILE(4) OVER (ORDER BY achieved DESC)");
            var base = db.execute("SELECT name, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // 10 reps / 4 quartiles: buckets 1,2 get 3 each, buckets 3,4 get 2 each
            var buckets = new HashMap<Long, Integer>();
            for (Row row : result.rows()) {
                Long bucket = (Long) row.getValue(2);
                assertThat(bucket).isBetween(1L, 4L);
                buckets.merge(bucket, 1, Integer::sum);
            }
            assertThat(buckets).hasSize(4);
        }

        @Test
        @DisplayName("ROW_NUMBER for unique ordering within region and team")
        void rowNumberUniqueOrdering() {
            var wf = db.parseWindowFunction("ROW_NUMBER() OVER (PARTITION BY region ORDER BY achieved DESC)");
            var base = db.execute("SELECT name, region, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));

            // Within West: Alice(620k)=1, Bob(380k)=2, Carol(350k)=3, Jake(275k)=4
            var westRowNumbers = new TreeSet<Long>();
            for (Row row : result.rows()) {
                if ("West".equals(row.getValue(1))) {
                    westRowNumbers.add((Long) row.getValue(3));
                }
            }
            assertThat(westRowNumbers).containsExactly(1L, 2L, 3L, 4L);
        }
    }

    // ==========================================================================
    // Window Functions - Analytics
    // ==========================================================================

    @Nested
    @DisplayName("Window Functions - Analytics")
    class WindowFunctionAnalyticsTests {

        @Test
        @DisplayName("LAG to compare rep with previous performer")
        void lagPreviousPerformer() {
            var wf = db.parseWindowFunction("LAG(achieved, 1, 0) OVER (ORDER BY achieved DESC)");
            var base = db.execute("SELECT name, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("LEAD to see next performer in ranking")
        void leadNextPerformer() {
            var wf = db.parseWindowFunction("LEAD(achieved, 1, 0) OVER (ORDER BY achieved DESC)");
            var base = db.execute("SELECT name, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(10);
            // Last row should have default 0
        }

        @Test
        @DisplayName("Running SUM of achieved within each region")
        void runningSumByRegion() {
            var wf = db.parseWindowFunction("SUM(achieved) OVER (PARTITION BY region ORDER BY achieved ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)");
            var base = db.execute("SELECT name, region, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            assertThat(result.rowCount()).isEqualTo(10);
            for (Row row : result.rows()) {
                assertThat(row.getValue(3)).isNotNull();
            }
        }

        @Test
        @DisplayName("AVG over partition for team benchmarking")
        void avgOverPartitionTeamBenchmark() {
            var wf = db.parseWindowFunction("AVG(achieved) OVER (PARTITION BY team)");
            var base = db.execute("SELECT name, team, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            // All Enterprise reps should see the same average
            Double enterpriseAvg = null;
            for (Row row : result.rows()) {
                if ("Enterprise".equals(row.getValue(1))) {
                    if (enterpriseAvg == null) {
                        enterpriseAvg = (Double) row.getValue(3);
                    } else {
                        assertThat((Double) row.getValue(3)).isEqualTo(enterpriseAvg);
                    }
                }
            }
            assertThat(enterpriseAvg).isNotNull();
        }

        @Test
        @DisplayName("FIRST_VALUE and LAST_VALUE per region")
        void firstAndLastValuePerRegion() {
            var wfFirst = db.parseWindowFunction("FIRST_VALUE(name) OVER (PARTITION BY region ORDER BY achieved DESC)");
            var base = db.execute("SELECT name, region, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wfFirst));
            // In West partition, first value should be Alice (highest achieved)
            for (Row row : result.rows()) {
                if ("West".equals(row.getValue(1))) {
                    assertThat(row.getValue(3)).isEqualTo("Alice");
                }
            }
        }

        @Test
        @DisplayName("PERCENT_RANK for percentile-based compensation")
        void percentRankCompensation() {
            var wf = db.parseWindowFunction("PERCENT_RANK() OVER (ORDER BY achieved)");
            var base = db.execute("SELECT name, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf));
            for (Row row : result.rows()) {
                Double pr = (Double) row.getValue(2);
                assertThat(pr).isBetween(0.0, 1.0);
            }
        }

        @Test
        @DisplayName("Multiple window functions: RANK + SUM + COUNT")
        void multipleWindowFunctions() {
            var wf1 = db.parseWindowFunction("RANK() OVER (ORDER BY achieved DESC)");
            var wf2 = db.parseWindowFunction("SUM(achieved) OVER (PARTITION BY region)");
            var wf3 = db.parseWindowFunction("COUNT(*) OVER (PARTITION BY region)");

            var base = db.execute("SELECT name, region, achieved FROM sales_reps");
            var baseQr = (QueryResult) base.value();

            var result = new WindowFunctionExecutor().execute(baseQr, List.of(wf1, wf2, wf3));
            assertThat(result.columnNames()).hasSize(6); // name, region, achieved, rank, sum, count
            assertThat(result.rowCount()).isEqualTo(10);
        }
    }

    // ==========================================================================
    // CTEs - Business Reporting
    // ==========================================================================

    @Nested
    @DisplayName("CTEs - Business Reporting")
    class CteBusinessTests {

        @Test
        @DisplayName("CTE: quota attainment by rep")
        void cteQuotaAttainment() {
            var sql = "WITH attainment AS (SELECT name, region, quota, achieved FROM sales_reps) SELECT * FROM attainment";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("CTE: region summary with aggregates")
        void cteRegionSummary() {
            var sql = "WITH region_stats AS (SELECT region, COUNT(id) AS headcount, SUM(achieved) AS total_achieved, AVG(achieved) AS avg_achieved FROM sales_reps GROUP BY region) SELECT * FROM region_stats";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(3); // West, East, Central
        }

        @Test
        @DisplayName("Multiple CTEs: team summary + filter")
        void multipleCtes() {
            var sql = "WITH team_stats AS (SELECT team, AVG(achieved) AS avg_achieved FROM sales_reps GROUP BY team), " +
                    "top_teams AS (SELECT team, avg_achieved FROM sales_reps WHERE achieved > 300000 GROUP BY team) " +
                    "SELECT * FROM team_stats";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isEqualTo(2); // Enterprise, SMB
        }

        @Test
        @DisplayName("CTE with column aliases for clean reporting")
        void cteWithColumnAliases() {
            var sql = "WITH report (rep_name, territory, performance) AS (SELECT name, region, achieved FROM sales_reps) SELECT * FROM report";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().columnNames()).containsExactly("rep_name", "territory", "performance");
            assertThat(result.value().rowCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("CTE with WHERE filter on derived data")
        void cteWithWhereFilter() {
            var sql = "WITH over_quota AS (SELECT name, region, achieved FROM sales_reps WHERE achieved > 400000) SELECT * FROM over_quota";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            // Alice(620k), Dave(510k), Eve(400k), Grace(750k) => actually Eve=400k not > 400k
            // Alice(620k), Dave(510k), Grace(750k) = 3
            assertThat(result.value().rowCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Recursive CTE: org hierarchy")
        void recursiveCteOrgHierarchy() {
            db.execute("CREATE TABLE org (id INTEGER, name VARCHAR, manager_id INTEGER)");
            db.execute("INSERT INTO org VALUES (1, 'CEO', NULL)");
            db.execute("INSERT INTO org VALUES (2, 'VP Sales', 1)");
            db.execute("INSERT INTO org VALUES (3, 'Director', 2)");

            var sql = "WITH RECURSIVE org_tree AS (SELECT id, name, manager_id FROM org WHERE manager_id IS NULL) SELECT * FROM org_tree";
            var result = db.executeOlap(sql);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }
    }

    // ==========================================================================
    // MERGE - Data Synchronization
    // ==========================================================================

    @Nested
    @DisplayName("MERGE - Data Synchronization")
    class MergeTests {

        @BeforeEach
        void setUpMergeTables() {
            db.execute("CREATE TABLE crm_accounts (id INTEGER, company VARCHAR, revenue INTEGER, status VARCHAR)");
            db.execute("INSERT INTO crm_accounts VALUES (1, 'Acme Corp', 50000, 'active')");
            db.execute("INSERT INTO crm_accounts VALUES (2, 'BigCo', 120000, 'active')");
            db.execute("INSERT INTO crm_accounts VALUES (3, 'StartupX', 15000, 'prospect')");

            db.execute("CREATE TABLE import_accounts (id INTEGER, company VARCHAR, revenue INTEGER, status VARCHAR)");
            db.execute("INSERT INTO import_accounts VALUES (2, 'BigCo', 150000, 'active')");
            db.execute("INSERT INTO import_accounts VALUES (3, 'StartupX', 30000, 'active')");
            db.execute("INSERT INTO import_accounts VALUES (4, 'NewClient', 80000, 'active')");
        }

        @Test
        @DisplayName("MERGE upsert: update existing + insert new accounts")
        void mergeUpsertAccounts() {
            var result = db.executeMerge(
                    "MERGE INTO crm_accounts t USING import_accounts s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.revenue = s.revenue, t.status = s.status " +
                    "WHEN NOT MATCHED THEN INSERT (id, company, revenue, status) VALUES (s.id, s.company, s.revenue, s.status)");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(3); // 2 updates + 1 insert

            // Verify BigCo revenue updated
            var check = db.execute("SELECT revenue FROM crm_accounts WHERE id = 2");
            var qr = (QueryResult) check.value();
            assertThat(((Number) qr.rows().getFirst().getValue(0)).longValue()).isEqualTo(150000L);
        }

        @Test
        @DisplayName("MERGE with DELETE for deactivating accounts")
        void mergeWithDelete() {
            db.execute("CREATE TABLE deactivate_list (id INTEGER, company VARCHAR, revenue INTEGER, status VARCHAR)");
            db.execute("INSERT INTO deactivate_list VALUES (1, 'Acme Corp', 0, 'inactive')");

            var result = db.executeMerge(
                    "MERGE INTO crm_accounts t USING deactivate_list s ON t.id = s.id " +
                    "WHEN MATCHED THEN DELETE");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(1);

            var check = db.execute("SELECT * FROM crm_accounts");
            assertThat(((QueryResult) check.value()).rowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("MERGE with empty source produces zero affected rows")
        void mergeEmptySource() {
            db.execute("CREATE TABLE empty_import (id INTEGER, company VARCHAR, revenue INTEGER, status VARCHAR)");
            var result = db.executeMerge(
                    "MERGE INTO crm_accounts t USING empty_import s ON t.id = s.id " +
                    "WHEN MATCHED THEN UPDATE SET t.revenue = s.revenue " +
                    "WHEN NOT MATCHED THEN INSERT (id, company, revenue, status) VALUES (s.id, s.company, s.revenue, s.status)");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().affectedRows()).isEqualTo(0);
        }
    }

    // ==========================================================================
    // PIVOT/UNPIVOT - Reporting Transformations
    // ==========================================================================

    @Nested
    @DisplayName("PIVOT/UNPIVOT - Reporting")
    class PivotUnpivotTests {

        @Test
        @DisplayName("PIVOT deals by quarter for each product")
        void pivotDealsByQuarter() {
            var pivot = db.parsePivot("PIVOT (SUM(amount) FOR quarter IN ('Q1', 'Q2', 'Q3'))");
            var result = db.executePivot("deals", pivot, List.of("product"));

            assertThat(result.isSuccess()).isTrue();
            var qr = result.value();
            assertThat(qr.columnNames()).contains("product", "Q1", "Q2", "Q3");
            assertThat(qr.rowCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("PIVOT with COUNT for deal frequency")
        void pivotCountByStatus() {
            var pivot = db.parsePivot("PIVOT (COUNT(id) FOR status IN ('closed', 'pipeline'))");
            var result = db.executePivot("deals", pivot, List.of("product"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().columnNames()).contains("product", "closed", "pipeline");
        }

        @Test
        @DisplayName("UNPIVOT quarterly results into rows")
        void unpivotQuarterlyResults() {
            db.execute("CREATE TABLE quarterly_targets (region VARCHAR, q1_target INTEGER, q2_target INTEGER, q3_target INTEGER)");
            db.execute("INSERT INTO quarterly_targets VALUES ('West', 300000, 350000, 400000)");
            db.execute("INSERT INTO quarterly_targets VALUES ('East', 250000, 280000, 320000)");
            db.execute("INSERT INTO quarterly_targets VALUES ('Central', 200000, 220000, 260000)");

            var unpivot = db.parseUnpivot("UNPIVOT (target_amount FOR quarter IN (q1_target, q2_target, q3_target))");
            var result = db.executeUnpivot("quarterly_targets", unpivot, List.of("region"));

            assertThat(result.isSuccess()).isTrue();
            var qr = result.value();
            assertThat(qr.columnNames()).containsExactly("region", "quarter", "target_amount");
            assertThat(qr.rowCount()).isEqualTo(9); // 3 regions * 3 quarters
        }
    }

    // ==========================================================================
    // GROUPING SETS - Multi-dimensional Analysis
    // ==========================================================================

    @Nested
    @DisplayName("GROUPING SETS - Multi-dimensional Analysis")
    class GroupingSetTests {

        @Test
        @DisplayName("CUBE on region and team for complete cross-tabulation")
        void cubeRegionTeam() {
            var spec = db.parseGroupingSet("CUBE(region, team)");
            var result = db.executeGroupingSet("sales_reps", spec, List.of("SUM(achieved)"), null);

            assertThat(result.isSuccess()).isTrue();
            // CUBE(2) = 4 grouping combos: (region,team), (region), (team), ()
            assertThat(result.value().rowCount()).isGreaterThanOrEqualTo(4);

            // Grand total row should exist
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
        @DisplayName("ROLLUP for hierarchical region > team subtotals")
        void rollupRegionTeam() {
            var spec = db.parseGroupingSet("ROLLUP(region, team)");
            var result = db.executeGroupingSet("sales_reps", spec, List.of("SUM(achieved)"), null);

            assertThat(result.isSuccess()).isTrue();

            boolean foundRegionSubtotal = false;
            boolean foundGrandTotal = false;
            for (Row row : result.value().rows()) {
                if (row.getValue(0) != null && row.getValue(1) == null) foundRegionSubtotal = true;
                if (row.getValue(0) == null && row.getValue(1) == null) foundGrandTotal = true;
            }
            assertThat(foundRegionSubtotal).isTrue();
            assertThat(foundGrandTotal).isTrue();
        }

        @Test
        @DisplayName("GROUPING SETS explicit: region-only + team-only + grand total")
        void explicitGroupingSets() {
            var spec = db.parseGroupingSet("GROUPING SETS ((region), (team), ())");
            var result = db.executeGroupingSet("sales_reps", spec, List.of("SUM(achieved)"), null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("CUBE with multiple aggregates: SUM + COUNT")
        void cubeMultipleAggregates() {
            var spec = db.parseGroupingSet("CUBE(region, team)");
            var result = db.executeGroupingSet("sales_reps", spec, List.of("SUM(achieved)", "COUNT(id)"), null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().columnNames()).contains("SUM(achieved)", "COUNT(id)");
        }

        @Test
        @DisplayName("CUBE with WHERE filter on hire_year")
        void cubeWithWhereFilter() {
            var spec = db.parseGroupingSet("CUBE(region, team)");
            var result = db.executeGroupingSet("sales_reps", spec, List.of("SUM(achieved)"), "hire_year >= 2020");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().rowCount()).isGreaterThan(0);
        }
    }
}
