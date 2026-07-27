package ssg.pex.sql.dialects.engines;

import ssg.pex.result.Result;
import ssg.pex.sql.dbms.*;
import ssg.pex.sql.dbms.result.QueryResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full-text search engine.
 * Supports FULLTEXT INDEX creation, MATCH...AGAINST queries with boolean mode.
 */
public class FullTextEngine {

    private static final Pattern CREATE_FT_INDEX_PATTERN = Pattern.compile(
            "(?i)CREATE\\s+FULLTEXT\\s+INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\(([^)]+)\\)");
    private static final Pattern MATCH_AGAINST_PATTERN = Pattern.compile(
            "(?i)MATCH\\s*\\(([^)]+)\\)\\s+AGAINST\\s*\\(\\s*'([^']*)'(?:\\s+IN\\s+BOOLEAN\\s+MODE)?\\s*\\)");

    /**
     * Inverted index for full-text search.
     */
    public static class FullTextIndex {
        private final String name;
        private final String tableName;
        private final List<String> columns;
        // term -> set of row indices
        private final Map<String, Set<Integer>> invertedIndex;

        public FullTextIndex(String name, String tableName, List<String> columns) {
            this.name = name;
            this.tableName = tableName;
            this.columns = new ArrayList<>(columns);
            this.invertedIndex = new HashMap<>();
        }

        public void indexRow(int rowIdx, String text) {
            String[] tokens = tokenize(text);
            for (String token : tokens) {
                invertedIndex.computeIfAbsent(token.toLowerCase(), k -> new HashSet<>()).add(rowIdx);
            }
        }

        public Set<Integer> search(String term) {
            return invertedIndex.getOrDefault(term.toLowerCase(), Set.of());
        }

        /**
         * Relevance scoring: count of matching terms.
         */
        public double relevanceScore(int rowIdx, String[] searchTerms) {
            double score = 0;
            for (String term : searchTerms) {
                Set<Integer> rows = invertedIndex.getOrDefault(term.toLowerCase(), Set.of());
                if (rows.contains(rowIdx)) {
                    // TF-IDF simplified: 1 + log(totalDocs / matchingDocs) for positive scores
                    double idf = 1.0 + Math.log((double) totalDocuments() / Math.max(rows.size(), 1));
                    score += idf;
                }
            }
            return score;
        }

        private int totalDocuments() {
            Set<Integer> allDocs = new HashSet<>();
            for (Set<Integer> docs : invertedIndex.values()) {
                allDocs.addAll(docs);
            }
            return Math.max(allDocs.size(), 1);
        }

        private String[] tokenize(String text) {
            if (text == null) return new String[0];
            // Simple whitespace and punctuation tokenization
            return text.toLowerCase()
                    .replaceAll("[^a-zA-Z0-9\\s]", " ")
                    .trim()
                    .split("\\s+");
        }

        public String name() { return name; }
        public String tableName() { return tableName; }
        public List<String> columns() { return columns; }
    }

    private final Map<String, FullTextIndex> indexes = new ConcurrentHashMap<>();

    public Result<Object> execute(String sql, InMemoryDatabase db) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();

        // CREATE FULLTEXT INDEX
        if (upper.startsWith("CREATE FULLTEXT INDEX")) {
            return createFullTextIndex(trimmed, db);
        }

        // MATCH ... AGAINST
        if (upper.contains("MATCH") && upper.contains("AGAINST")) {
            return executeMatchAgainst(trimmed, db);
        }

        return db.execute(trimmed);
    }

    private Result<Object> createFullTextIndex(String sql, InMemoryDatabase db) {
        Matcher m = CREATE_FT_INDEX_PATTERN.matcher(sql);
        if (!m.find()) {
            return Result.failure("FULLTEXT_ERROR", "Invalid CREATE FULLTEXT INDEX syntax");
        }

        String indexName = m.group(1);
        String tableName = m.group(2);
        String[] cols = m.group(3).split(",");
        List<String> columns = new ArrayList<>();
        for (String c : cols) columns.add(c.trim());

        Table table = db.defaultSchema().getTable(tableName);
        if (table == null) {
            return Result.failure("FULLTEXT_ERROR", "Table not found: " + tableName);
        }

        FullTextIndex ftIndex = new FullTextIndex(indexName, tableName, columns);

        // Build inverted index from existing rows
        List<Row> rows = table.scan();
        for (int i = 0; i < rows.size(); i++) {
            StringBuilder text = new StringBuilder();
            for (String col : columns) {
                int colIdx = table.getColumnIndex(col);
                if (colIdx >= 0) {
                    Object val = rows.get(i).getValue(colIdx);
                    if (val != null) {
                        if (!text.isEmpty()) text.append(' ');
                        text.append(val.toString());
                    }
                }
            }
            ftIndex.indexRow(i, text.toString());
        }

        indexes.put(indexName.toLowerCase(), ftIndex);
        return Result.success(null);
    }

    private Result<Object> executeMatchAgainst(String sql, InMemoryDatabase db) {
        Matcher matchMatcher = MATCH_AGAINST_PATTERN.matcher(sql);
        if (!matchMatcher.find()) {
            return Result.failure("FULLTEXT_ERROR", "Invalid MATCH...AGAINST syntax");
        }

        String matchCols = matchMatcher.group(1).trim();
        String searchExpr = matchMatcher.group(2).trim();
        boolean booleanMode = sql.toUpperCase().contains("IN BOOLEAN MODE");

        // Extract table from FROM clause
        String upper = sql.toUpperCase();
        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx < 0) return Result.failure("FULLTEXT_ERROR", "Missing FROM clause");
        String afterFrom = sql.substring(fromIdx + 6).trim();
        String tableName = afterFrom.split("[\\s;]")[0];

        // Find the fulltext index for this table
        FullTextIndex ftIndex = null;
        for (FullTextIndex idx : indexes.values()) {
            if (idx.tableName().equalsIgnoreCase(tableName)) {
                ftIndex = idx;
                break;
            }
        }

        Table table = db.defaultSchema().getTable(tableName);
        if (table == null) {
            return Result.failure("FULLTEXT_ERROR", "Table not found: " + tableName);
        }

        List<Row> allRows = table.scan();
        List<Row> matchingRows = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        if (booleanMode) {
            // Boolean mode: +required -excluded "exact phrase"
            matchingRows = executeBooleanSearch(searchExpr, table, allRows, matchCols, ftIndex);
            for (Row ignored : matchingRows) scores.add(1.0); // simplified scoring
        } else {
            // Natural language mode
            String[] terms = searchExpr.toLowerCase().split("\\s+");

            if (ftIndex != null) {
                // Use index
                Set<Integer> matchedRowIndices = new HashSet<>();
                for (String term : terms) {
                    matchedRowIndices.addAll(ftIndex.search(term));
                }

                // Sort by relevance
                List<int[]> ranked = new ArrayList<>();
                for (int idx : matchedRowIndices) {
                    if (idx < allRows.size()) {
                        double score = ftIndex.relevanceScore(idx, terms);
                        ranked.add(new int[]{idx, (int) (score * 1000)});
                    }
                }
                ranked.sort((a, b) -> b[1] - a[1]);
                for (int[] r : ranked) {
                    matchingRows.add(allRows.get(r[0]));
                    scores.add(r[1] / 1000.0);
                }
            } else {
                // Brute-force text scan
                String[] matchColArr = matchCols.split(",");
                for (int i = 0; i < allRows.size(); i++) {
                    Row row = allRows.get(i);
                    StringBuilder text = new StringBuilder();
                    for (String col : matchColArr) {
                        int colIdx = table.getColumnIndex(col.trim());
                        if (colIdx >= 0) {
                            Object val = row.getValue(colIdx);
                            if (val != null) text.append(' ').append(val.toString().toLowerCase());
                        }
                    }
                    String rowText = text.toString();
                    boolean match = false;
                    double score = 0;
                    for (String term : terms) {
                        if (rowText.contains(term)) {
                            match = true;
                            score += 1.0;
                        }
                    }
                    if (match) {
                        matchingRows.add(row);
                        scores.add(score);
                    }
                }
            }
        }

        // Parse SELECT columns
        int selectIdx = upper.indexOf("SELECT ") + 7;
        int fromIdxForSelect = upper.indexOf(" FROM ");
        String selectPart = sql.substring(selectIdx, fromIdxForSelect).trim();

        // Check if SELECT includes MATCH...AGAINST for score
        List<String> colNames = new ArrayList<>();
        boolean includesScore = false;
        if (selectPart.equals("*")) {
            for (Column col : table.columns()) colNames.add(col.name());
        } else {
            // Simplified: just use column names from select
            for (Column col : table.columns()) colNames.add(col.name());
            if (selectPart.toUpperCase().contains("MATCH")) {
                colNames.add("relevance");
                includesScore = true;
            }
        }

        // Build result
        List<Row> resultRows = new ArrayList<>();
        for (int i = 0; i < matchingRows.size(); i++) {
            Row row = matchingRows.get(i);
            if (includesScore) {
                Object[] vals = new Object[row.columnCount() + 1];
                for (int j = 0; j < row.columnCount(); j++) vals[j] = row.getValue(j);
                vals[row.columnCount()] = scores.get(i);
                resultRows.add(new Row(vals));
            } else {
                resultRows.add(row);
            }
        }

        return Result.success(new QueryResult(colNames, resultRows, 0));
    }

    private List<Row> executeBooleanSearch(String expr, Table table, List<Row> rows,
                                           String matchCols, FullTextIndex ftIndex) {
        // Parse boolean expression
        List<String> required = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        List<String> exactPhrases = new ArrayList<>();

        // Extract exact phrases first
        Pattern phrasePattern = Pattern.compile("\"([^\"]+)\"");
        Matcher phraseMatcher = phrasePattern.matcher(expr);
        while (phraseMatcher.find()) {
            exactPhrases.add(phraseMatcher.group(1).toLowerCase());
        }
        String remaining = expr.replaceAll("\"[^\"]+\"", "").trim();

        // Parse +required -excluded terms
        for (String token : remaining.split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.startsWith("+")) {
                required.add(token.substring(1).toLowerCase());
            } else if (token.startsWith("-")) {
                excluded.add(token.substring(1).toLowerCase());
            } else {
                optional.add(token.toLowerCase());
            }
        }

        String[] matchColArr = matchCols.split(",");
        List<Row> results = new ArrayList<>();

        for (Row row : rows) {
            StringBuilder text = new StringBuilder();
            for (String col : matchColArr) {
                int colIdx = table.getColumnIndex(col.trim());
                if (colIdx >= 0) {
                    Object val = row.getValue(colIdx);
                    if (val != null) text.append(' ').append(val.toString().toLowerCase());
                }
            }
            String rowText = text.toString();

            // Check required terms
            boolean allRequired = required.stream().allMatch(t -> rowText.contains(t));
            if (!allRequired && !required.isEmpty()) continue;

            // Check excluded terms
            boolean anyExcluded = excluded.stream().anyMatch(t -> rowText.contains(t));
            if (anyExcluded) continue;

            // Check exact phrases
            boolean allPhrases = exactPhrases.stream().allMatch(p -> rowText.contains(p));
            if (!allPhrases && !exactPhrases.isEmpty()) continue;

            // If no required terms, need at least one optional/phrase match
            if (required.isEmpty() && exactPhrases.isEmpty()) {
                boolean anyOptional = optional.stream().anyMatch(t -> rowText.contains(t));
                if (!anyOptional) continue;
            }

            results.add(row);
        }

        return results;
    }

    public FullTextIndex getIndex(String name) {
        return indexes.get(name.toLowerCase());
    }

    public Map<String, FullTextIndex> indexes() {
        return Collections.unmodifiableMap(indexes);
    }
}
