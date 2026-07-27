package ssg.pex.sql.dialects;

import org.junit.jupiter.api.*;
import ssg.pex.sql.dbms.InMemoryDatabase;
import ssg.pex.sql.dbms.result.DmlResult;
import ssg.pex.sql.dbms.result.QueryResult;
import ssg.pex.sql.dialects.mssql.MssqlFunctions;
import ssg.pex.sql.dialects.mysql.MysqlFunctions;
import ssg.pex.sql.dialects.oracle.OracleFunctions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * Real-world dialect tests with business scenarios across Oracle, MSSQL, and MySQL.
 */
class RealWorldDialectTest {

    // ==========================================================================
    // Oracle: ERP and Financial Reporting
    // ==========================================================================

    @Nested
    @DisplayName("Oracle - ERP and Financial Reporting")
    class OracleTests {

        private DialectDatabase oracleDb;

        @BeforeEach
        void setUp() {
            oracleDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.ORACLE);

            oracleDb.execute("CREATE TABLE gl_accounts (id INTEGER PRIMARY KEY, account_code VARCHAR(20), account_name VARCHAR(100), balance DOUBLE, currency VARCHAR(3))");
            oracleDb.execute("INSERT INTO gl_accounts (id, account_code, account_name, balance, currency) VALUES (1, '1000', 'Cash', 500000.0, 'USD')");
            oracleDb.execute("INSERT INTO gl_accounts (id, account_code, account_name, balance, currency) VALUES (2, '1200', 'Accounts Receivable', 250000.0, 'USD')");
            oracleDb.execute("INSERT INTO gl_accounts (id, account_code, account_name, balance, currency) VALUES (3, '2000', 'Accounts Payable', 180000.0, 'USD')");
            oracleDb.execute("INSERT INTO gl_accounts (id, account_code, account_name, balance, currency) VALUES (4, '3000', 'Revenue', 1200000.0, 'USD')");
            oracleDb.execute("INSERT INTO gl_accounts (id, account_code, account_name, balance, currency) VALUES (5, '4000', 'Expenses', 800000.0, 'USD')");
            oracleDb.execute("INSERT INTO gl_accounts (id, account_code, account_name, balance, currency) VALUES (6, '1100', 'Petty Cash', NULL, 'EUR')");
        }

        @Test
        @DisplayName("SELECT from DUAL for computed values")
        void selectFromDualComputed() {
            var result = oracleDb.execute("SELECT 12 * 30 + 5 FROM DUAL");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(365L);
        }

        @Test
        @DisplayName("NVL to handle NULL balance for reporting")
        void nvlForNullBalance() {
            var result = oracleDb.execute("SELECT NVL(NULL, 0) FROM DUAL");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(0L);
        }

        @Test
        @DisplayName("NVL2 for conditional display of balance status")
        void nvl2ConditionalStatus() {
            var result = oracleDb.execute("SELECT NVL2('active', 'Has Value', 'No Value') FROM DUAL");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("Has Value");
        }

        @Test
        @DisplayName("DECODE for account type classification")
        void decodeAccountType() {
            var result = oracleDb.execute("SELECT DECODE('1000', '1000', 'Asset', '2000', 'Liability', '3000', 'Revenue', 'Other') FROM DUAL");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("Asset");
        }

        @Test
        @DisplayName("DECODE with no match returns default")
        void decodeNoMatchDefault() {
            var result = oracleDb.execute("SELECT DECODE('9999', '1000', 'Asset', '2000', 'Liability', 'Unknown') FROM DUAL");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("Oracle function: TO_CHAR converts number to string")
        void toCharNumberConversion() {
            var registry = new DialectFunctionRegistry();
            OracleFunctions.register(registry);
            Object result = registry.get("TO_CHAR").apply(java.util.List.of(12345));
            assertThat(result).isEqualTo("12345");
        }

        @Test
        @DisplayName("Oracle function: TO_NUMBER converts string to number")
        void toNumberStringConversion() {
            var registry = new DialectFunctionRegistry();
            OracleFunctions.register(registry);
            Object result = registry.get("TO_NUMBER").apply(java.util.List.of("42"));
            assertThat(result).isEqualTo(42L);
        }

        @Test
        @DisplayName("Oracle function: SYSDATE returns today's date")
        void sysdateReturnsToday() {
            var registry = new DialectFunctionRegistry();
            OracleFunctions.register(registry);
            Object result = registry.get("SYSDATE").apply(java.util.List.of());
            assertThat(result.toString()).startsWith(LocalDate.now().toString().substring(0, 4));
        }

        @Test
        @DisplayName("Oracle function: MONTHS_BETWEEN calculates month difference")
        void monthsBetween() {
            var registry = new DialectFunctionRegistry();
            OracleFunctions.register(registry);
            Object result = registry.get("MONTHS_BETWEEN").apply(java.util.List.of("2026-06-15", "2026-01-15"));
            assertThat(((Number) result).longValue()).isEqualTo(5L);
        }

        @Test
        @DisplayName("Oracle function: ADD_MONTHS for fiscal period calculation")
        void addMonthsFiscalPeriod() {
            var registry = new DialectFunctionRegistry();
            OracleFunctions.register(registry);
            Object result = registry.get("ADD_MONTHS").apply(java.util.List.of("2026-01-15", 3L));
            assertThat(result).isEqualTo("2026-04-15");
        }

        @Test
        @DisplayName("Oracle: SELECT with table data")
        void selectTableData() {
            var result = oracleDb.execute("SELECT * FROM gl_accounts");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rowCount()).isEqualTo(6);
        }

        @Test
        @DisplayName("Oracle: REGEXP_LIKE for account code validation")
        void regexpLikeAccountCode() {
            var registry = new DialectFunctionRegistry();
            OracleFunctions.register(registry);
            // Pattern: exactly 4 digits
            Object result = registry.get("REGEXP_LIKE").apply(java.util.List.of("1000", "^[0-9]{4}$"));
            assertThat(result).isEqualTo(true);
        }

        @Test
        @DisplayName("Oracle dialect type is ORACLE")
        void dialectType() {
            assertThat(oracleDb.dialect()).isEqualTo(DialectDatabase.DialectType.ORACLE);
        }

        @Test
        @DisplayName("Oracle function registry is complete")
        void functionRegistryComplete() {
            assertThat(oracleDb.functionRegistry().has("NVL")).isTrue();
            assertThat(oracleDb.functionRegistry().has("NVL2")).isTrue();
            assertThat(oracleDb.functionRegistry().has("DECODE")).isTrue();
            assertThat(oracleDb.functionRegistry().has("TO_CHAR")).isTrue();
            assertThat(oracleDb.functionRegistry().has("TO_NUMBER")).isTrue();
            assertThat(oracleDb.functionRegistry().has("SYSDATE")).isTrue();
        }
    }

    // ==========================================================================
    // MSSQL: Enterprise Data Warehouse
    // ==========================================================================

    @Nested
    @DisplayName("MSSQL - Enterprise Data Warehouse")
    class MssqlTests {

        private DialectDatabase mssqlDb;

        @BeforeEach
        void setUp() {
            mssqlDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.MSSQL);

            mssqlDb.execute("CREATE TABLE dim_customer (id INTEGER PRIMARY KEY, name VARCHAR(100), segment VARCHAR(50), region VARCHAR(50), credit_limit INTEGER)");
            mssqlDb.execute("INSERT INTO dim_customer (id, name, segment, region, credit_limit) VALUES (1, 'Acme Corp', 'Enterprise', 'North', 500000)");
            mssqlDb.execute("INSERT INTO dim_customer (id, name, segment, region, credit_limit) VALUES (2, 'BigCo', 'Enterprise', 'South', 750000)");
            mssqlDb.execute("INSERT INTO dim_customer (id, name, segment, region, credit_limit) VALUES (3, 'StartupX', 'SMB', 'North', 50000)");
            mssqlDb.execute("INSERT INTO dim_customer (id, name, segment, region, credit_limit) VALUES (4, 'MidSize', 'Mid-Market', 'East', 200000)");
            mssqlDb.execute("INSERT INTO dim_customer (id, name, segment, region, credit_limit) VALUES (5, 'TechCo', 'Enterprise', 'West', 600000)");
            mssqlDb.execute("INSERT INTO dim_customer (id, name, segment, region, credit_limit) VALUES (6, 'LocalShop', 'SMB', 'East', 25000)");
            mssqlDb.execute("INSERT INTO dim_customer (id, name, segment, region, credit_limit) VALUES (7, 'GlobalInc', 'Enterprise', 'North', 900000)");
            mssqlDb.execute("INSERT INTO dim_customer (id, name, segment, region, credit_limit) VALUES (8, 'NanoCorp', 'SMB', 'South', 30000)");
        }

        @Test
        @DisplayName("TOP N: Get top 3 customers by credit limit")
        void topNCustomersByCreditLimit() {
            var result = mssqlDb.execute("SELECT TOP 3 * FROM dim_customer ORDER BY credit_limit DESC");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rowCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("TOP PERCENT: Get top 25% of customers")
        void topPercentCustomers() {
            var result = mssqlDb.execute("SELECT TOP 25 PERCENT * FROM dim_customer");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rowCount()).isEqualTo(2); // 25% of 8
        }

        @Test
        @DisplayName("Variables: DECLARE, SET, SELECT for dynamic queries")
        void variablesForDynamicQueries() {
            mssqlDb.execute("DECLARE @segment VARCHAR");
            mssqlDb.execute("SET @segment = 'Enterprise'");
            var result = mssqlDb.execute("SELECT @segment");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo("Enterprise");
        }

        @Test
        @DisplayName("ISNULL for NULL handling in credit limit")
        void isnullNullHandling() {
            var registry = new DialectFunctionRegistry();
            MssqlFunctions.register(registry);
            Object result = registry.get("ISNULL").apply(java.util.Arrays.asList(null, 0));
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("CONVERT: INT to VARCHAR")
        void convertIntToVarchar() {
            var registry = new DialectFunctionRegistry();
            MssqlFunctions.register(registry);
            Object result = registry.get("CONVERT").apply(java.util.List.of("VARCHAR", 500000L));
            assertThat(result).isEqualTo("500000");
        }

        @Test
        @DisplayName("DATEDIFF: days between two dates")
        void datediffDays() {
            var registry = new DialectFunctionRegistry();
            MssqlFunctions.register(registry);
            Object result = registry.get("DATEDIFF").apply(java.util.List.of("DAY", "2026-01-01", "2026-06-04"));
            assertThat(((Number) result).longValue()).isEqualTo(154L);
        }

        @Test
        @DisplayName("DATEADD: add 90 days for payment due date")
        void dateaddPaymentDueDate() {
            var registry = new DialectFunctionRegistry();
            MssqlFunctions.register(registry);
            Object result = registry.get("DATEADD").apply(java.util.List.of("DAY", 90L, "2026-01-01"));
            assertThat(result).isEqualTo("2026-04-01");
        }

        @Test
        @DisplayName("STUFF for string manipulation in report formatting")
        void stuffForReportFormatting() {
            var registry = new DialectFunctionRegistry();
            MssqlFunctions.register(registry);
            Object result = registry.get("STUFF").apply(java.util.List.of("Customer: PLACEHOLDER", 11L, 11L, "Acme Corp"));
            assertThat(result).isEqualTo("Customer: Acme Corp");
        }

        @Test
        @DisplayName("CHARINDEX for substring search")
        void charindexSubstringSearch() {
            var registry = new DialectFunctionRegistry();
            MssqlFunctions.register(registry);
            Object result = registry.get("CHARINDEX").apply(java.util.List.of("Corp", "Acme Corp Ltd"));
            assertThat(((Number) result).longValue()).isEqualTo(6L);
        }

        @Test
        @DisplayName("FORMAT for currency display")
        void formatCurrencyDisplay() {
            var registry = new DialectFunctionRegistry();
            MssqlFunctions.register(registry);
            Object result = registry.get("FORMAT").apply(java.util.List.of(500000.0, "N0"));
            assertThat(result.toString()).contains("500,000");
        }

        @Test
        @DisplayName("NEWID generates unique identifier")
        void newidUniqueIdentifier() {
            var registry = new DialectFunctionRegistry();
            MssqlFunctions.register(registry);
            Object id1 = registry.get("NEWID").apply(java.util.List.of());
            Object id2 = registry.get("NEWID").apply(java.util.List.of());
            assertThat(id1).isNotEqualTo(id2);
            assertThat(id1.toString()).matches("[a-f0-9-]{36}");
        }

        @Test
        @DisplayName("INSERT with OUTPUT returns inserted IDs")
        void insertWithOutput() {
            var result = mssqlDb.execute(
                    "INSERT INTO dim_customer (id, name, segment, region, credit_limit) OUTPUT INSERTED.id VALUES (9, 'NewCo', 'SMB', 'West', 40000)");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rows().getFirst().getValue(0)).isEqualTo(9L);
        }

        @Test
        @DisplayName("WITH (NOLOCK) hint passes through")
        void nolockHint() {
            var result = mssqlDb.execute("SELECT * FROM dim_customer WITH (NOLOCK)");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("MSSQL dialect type is MSSQL")
        void dialectType() {
            assertThat(mssqlDb.dialect()).isEqualTo(DialectDatabase.DialectType.MSSQL);
        }
    }

    // ==========================================================================
    // MySQL: Web Application Backend
    // ==========================================================================

    @Nested
    @DisplayName("MySQL - Web Application Backend")
    class MysqlTests {

        private DialectDatabase mysqlDb;

        @BeforeEach
        void setUp() {
            mysqlDb = new DialectDatabase(new InMemoryDatabase(), DialectDatabase.DialectType.MYSQL);

            mysqlDb.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, username VARCHAR(50), email VARCHAR(100), status VARCHAR(20), created_at VARCHAR(20))");
            mysqlDb.execute("INSERT INTO users (id, username, email, status, created_at) VALUES (1, 'alice', 'alice@example.com', 'active', '2024-01-15')");
            mysqlDb.execute("INSERT INTO users (id, username, email, status, created_at) VALUES (2, 'bob', 'bob@example.com', 'active', '2024-02-20')");
            mysqlDb.execute("INSERT INTO users (id, username, email, status, created_at) VALUES (3, 'charlie', 'charlie@test.com', 'inactive', '2024-03-10')");
            mysqlDb.execute("INSERT INTO users (id, username, email, status, created_at) VALUES (4, 'diana', 'diana@example.com', 'active', '2024-04-05')");
            mysqlDb.execute("INSERT INTO users (id, username, email, status, created_at) VALUES (5, 'eve', 'eve@test.com', 'banned', '2024-05-01')");
        }

        @Test
        @DisplayName("REPLACE INTO: upsert user record")
        void replaceIntoUpsertUser() {
            var result = mysqlDb.execute("REPLACE INTO users (id, username, email, status, created_at) VALUES (1, 'alice_updated', 'alice_new@example.com', 'active', '2024-01-15')");
            assertThat(result.isSuccess()).isTrue();

            var check = mysqlDb.execute("SELECT * FROM users");
            var qr = (QueryResult) check.value();
            assertThat(qr.rowCount()).isEqualTo(5); // same count, row replaced
        }

        @Test
        @DisplayName("REPLACE INTO: insert new user")
        void replaceIntoNewUser() {
            mysqlDb.execute("REPLACE INTO users (id, username, email, status, created_at) VALUES (6, 'frank', 'frank@example.com', 'active', '2024-06-01')");
            var result = mysqlDb.execute("SELECT * FROM users");
            assertThat(((QueryResult) result.value()).rowCount()).isEqualTo(6);
        }

        @Test
        @DisplayName("SHOW TABLES lists all tables")
        void showTables() {
            mysqlDb.execute("CREATE TABLE sessions (id INTEGER PRIMARY KEY, user_id INTEGER, token VARCHAR(100))");
            var result = mysqlDb.execute("SHOW TABLES");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rowCount()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("SHOW DATABASES returns at least one database")
        void showDatabases() {
            var result = mysqlDb.execute("SHOW DATABASES");
            assertThat(result.isSuccess()).isTrue();
            assertThat(((QueryResult) result.value()).rowCount()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("DESCRIBE table shows columns and types")
        void describeTable() {
            var result = mysqlDb.execute("DESCRIBE users");
            assertThat(result.isSuccess()).isTrue();
            var qr = (QueryResult) result.value();
            assertThat(qr.rowCount()).isEqualTo(5);
            assertThat(qr.columnNames()).contains("Field", "Type");
        }

        @Test
        @DisplayName("DESC shorthand for DESCRIBE")
        void descShorthand() {
            var result = mysqlDb.execute("DESC users");
            assertThat(result.isSuccess()).isTrue();
            assertThat(((QueryResult) result.value()).rowCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("SHOW COLUMNS FROM table")
        void showColumnsFrom() {
            var result = mysqlDb.execute("SHOW COLUMNS FROM users");
            assertThat(result.isSuccess()).isTrue();
            assertThat(((QueryResult) result.value()).rowCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("MySQL function: IFNULL for default values")
        void ifnullDefaultValues() {
            var registry = new DialectFunctionRegistry();
            MysqlFunctions.register(registry);
            Object result = registry.get("IFNULL").apply(java.util.Arrays.asList(null, "N/A"));
            assertThat(result).isEqualTo("N/A");
        }

        @Test
        @DisplayName("MySQL function: IF conditional")
        void ifConditional() {
            var registry = new DialectFunctionRegistry();
            MysqlFunctions.register(registry);
            Object result = registry.get("IF").apply(java.util.List.of(true, "yes", "no"));
            assertThat(result).isEqualTo("yes");
        }

        @Test
        @DisplayName("MySQL function: GROUP_CONCAT for comma-separated list")
        void groupConcat() {
            var registry = new DialectFunctionRegistry();
            MysqlFunctions.register(registry);
            Object result = registry.get("GROUP_CONCAT").apply(java.util.List.of("alice", "bob", "charlie"));
            assertThat(result).isEqualTo("alice,bob,charlie");
        }

        @Test
        @DisplayName("MySQL function: LOCATE for substring position")
        void locateSubstring() {
            var registry = new DialectFunctionRegistry();
            MysqlFunctions.register(registry);
            Object result = registry.get("LOCATE").apply(java.util.List.of("@", "alice@example.com"));
            assertThat(((Number) result).longValue()).isEqualTo(6L);
        }

        @Test
        @DisplayName("MySQL function: LPAD for formatted output")
        void lpadFormatted() {
            var registry = new DialectFunctionRegistry();
            MysqlFunctions.register(registry);
            Object result = registry.get("LPAD").apply(java.util.List.of("42", 5L, "0"));
            assertThat(result).isEqualTo("00042");
        }

        @Test
        @DisplayName("MySQL function: RPAD for fixed-width output")
        void rpadFixedWidth() {
            var registry = new DialectFunctionRegistry();
            MysqlFunctions.register(registry);
            Object result = registry.get("RPAD").apply(java.util.List.of("Hi", 5L, "."));
            assertThat(result).isEqualTo("Hi...");
        }

        @Test
        @DisplayName("MySQL function: FIELD returns position in list")
        void fieldPosition() {
            var registry = new DialectFunctionRegistry();
            MysqlFunctions.register(registry);
            Object result = registry.get("FIELD").apply(java.util.List.of("b", "a", "b", "c"));
            assertThat(result).isEqualTo(2L);
        }

        @Test
        @DisplayName("MySQL function: FIND_IN_SET finds in comma-separated string")
        void findInSet() {
            var registry = new DialectFunctionRegistry();
            MysqlFunctions.register(registry);
            Object result = registry.get("FIND_IN_SET").apply(java.util.List.of("bob", "alice,bob,charlie"));
            assertThat(result).isEqualTo(2L);
        }

        @Test
        @DisplayName("MySQL dialect type is MYSQL")
        void dialectType() {
            assertThat(mysqlDb.dialect()).isEqualTo(DialectDatabase.DialectType.MYSQL);
        }

        @Test
        @DisplayName("MySQL function registry is complete")
        void functionRegistryComplete() {
            assertThat(mysqlDb.functionRegistry().has("IF")).isTrue();
            assertThat(mysqlDb.functionRegistry().has("IFNULL")).isTrue();
            assertThat(mysqlDb.functionRegistry().has("GROUP_CONCAT")).isTrue();
            assertThat(mysqlDb.functionRegistry().has("LOCATE")).isTrue();
            assertThat(mysqlDb.functionRegistry().has("LPAD")).isTrue();
            assertThat(mysqlDb.functionRegistry().has("RPAD")).isTrue();
            assertThat(mysqlDb.functionRegistry().has("FIELD")).isTrue();
            assertThat(mysqlDb.functionRegistry().has("FIND_IN_SET")).isTrue();
        }
    }
}
