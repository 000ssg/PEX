import ssg.pex.bnf.loader.GrammarLoader;
import ssg.pex.tools.docgen.GrammarDocGenerator;
import ssg.pex.tools.visualizer.DiagramStyle;

import java.nio.file.Path;
import java.util.LinkedHashMap;

public class GenerateGrammarDocs {

    public static void main(String[] args) {
        var loader = new GrammarLoader();
        var generator = new GrammarDocGenerator();
        var style = DiagramStyle.defaultStyle();

        String projectRoot = "/Users/sergey.sidorov/NetBeansProjects/pex";
        String outputDir = projectRoot + "/docs/grammar";

        var grammars = new LinkedHashMap<String, String>();

        grammars.put("base-expressions.html", "pex-base/src/main/resources/grammar/expressions.ebnf");
        grammars.put("base-functions.html", "pex-base/src/main/resources/grammar/functions.ebnf");
        grammars.put("base-literals.html", "pex-base/src/main/resources/grammar/literals.ebnf");
        grammars.put("base-operators.html", "pex-base/src/main/resources/grammar/operators.ebnf");
        grammars.put("arithmetic.html", "pex-arithmetics/src/main/resources/grammar/arithmetic.ebnf");
        grammars.put("bitwise.html", "pex-arithmetics/src/main/resources/grammar/bitwise.ebnf");
        grammars.put("radix.html", "pex-arithmetics/src/main/resources/grammar/radix.ebnf");
        grammars.put("math-functions.html", "pex-arithmetics/src/main/resources/grammar/math-functions.ebnf");
        grammars.put("sql-ddl.html", "pex-sql/pex-sql-core/src/main/resources/grammar/ddl.ebnf");
        grammars.put("sql-dml.html", "pex-sql/pex-sql-core/src/main/resources/grammar/dml.ebnf");
        grammars.put("sql-expressions.html", "pex-sql/pex-sql-core/src/main/resources/grammar/sql-expressions.ebnf");
        grammars.put("sql-transactions.html", "pex-sql/pex-sql-core/src/main/resources/grammar/transactions.ebnf");
        grammars.put("olap-window-functions.html", "pex-sql/pex-sql-olap/src/main/resources/grammar/window-functions.ebnf");
        grammars.put("olap-cte.html", "pex-sql/pex-sql-olap/src/main/resources/grammar/cte.ebnf");
        grammars.put("olap-grouping-sets.html", "pex-sql/pex-sql-olap/src/main/resources/grammar/grouping-sets.ebnf");
        grammars.put("olap-merge.html", "pex-sql/pex-sql-olap/src/main/resources/grammar/merge.ebnf");
        grammars.put("olap-pivot.html", "pex-sql/pex-sql-olap/src/main/resources/grammar/pivot.ebnf");
        grammars.put("streaming.html", "pex-sql/pex-sql-streaming/src/main/resources/grammar/streaming.ebnf");
        grammars.put("dialect-oracle.html", "pex-sql/pex-sql-dialects/src/main/resources/grammar/oracle.ebnf");
        grammars.put("dialect-mssql.html", "pex-sql/pex-sql-dialects/src/main/resources/grammar/mssql.ebnf");
        grammars.put("dialect-mysql.html", "pex-sql/pex-sql-dialects/src/main/resources/grammar/mysql.ebnf");
        grammars.put("dialect-postgresql.html", "pex-sql/pex-sql-dialects/src/main/resources/grammar/postgresql.ebnf");
        grammars.put("nosql-query.html", "pex-nosql/pex-nosql-core/src/main/resources/grammar/nosql-query.ebnf");
        grammars.put("nosql-operations.html", "pex-nosql/pex-nosql-core/src/main/resources/grammar/nosql-operations.ebnf");
        grammars.put("nosql-mongodb.html", "pex-nosql/pex-nosql-dialects/src/main/resources/grammar/mongodb.ebnf");
        grammars.put("nosql-cassandra.html", "pex-nosql/pex-nosql-dialects/src/main/resources/grammar/cassandra.ebnf");

        int success = 0;
        int failed = 0;

        for (var entry : grammars.entrySet()) {
            String outputFile = entry.getKey();
            String grammarPath = entry.getValue();
            Path inputPath = Path.of(projectRoot, grammarPath);
            Path outputPath = Path.of(outputDir, outputFile);

            System.out.printf("Processing: %s -> %s%n", grammarPath, outputFile);

            var loadResult = loader.loadFromFile(inputPath);
            if (loadResult.isFailure()) {
                System.err.printf("  SKIP (load failed): %s%n", loadResult);
                failed++;
                continue;
            }

            var grammar = loadResult.value();
            var genResult = generator.generateToFile(grammar, style, outputPath);
            if (genResult.isFailure()) {
                System.err.printf("  SKIP (gen failed): %s%n", genResult);
                failed++;
                continue;
            }

            System.out.printf("  OK: %s (%d rules)%n", outputFile, grammar.rules().size());
            success++;
        }

        System.out.printf("%nDone: %d generated, %d failed%n", success, failed);
    }
}
