package ssg.pex.bnf.engine;

import ssg.pex.ast.SourceLocation;
import ssg.pex.bnf.model.Grammar;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class ParseContext {

    private final String input;
    private final Grammar grammar;
    private final Deque<Long> callStack = new ArrayDeque<>();
    private final Map<Long, ParseMatch> memoTable = new HashMap<>();
    private final Deque<Integer> marks = new ArrayDeque<>();

    private int cursor;
    private int maxCursor;
    private String expectedAtMaxCursor;

    public ParseContext(String input, Grammar grammar) {
        this.input = input;
        this.grammar = grammar;
        this.cursor = 0;
        this.maxCursor = 0;
        this.expectedAtMaxCursor = null;
    }

    public String input() {
        return input;
    }

    public Grammar grammar() {
        return grammar;
    }

    public int cursor() {
        return cursor;
    }

    public void setCursor(int position) {
        this.cursor = position;
    }

    public char peek() {
        return input.charAt(cursor);
    }

    public char advance() {
        char c = input.charAt(cursor);
        cursor++;
        if (cursor > maxCursor) {
            maxCursor = cursor;
            expectedAtMaxCursor = null;
        }
        return c;
    }

    public boolean isAtEnd() {
        return cursor >= input.length();
    }

    public String remaining() {
        return input.substring(cursor);
    }

    public int maxCursor() {
        return maxCursor;
    }

    public String expectedAtMaxCursor() {
        return expectedAtMaxCursor;
    }

    public void recordExpected(String expected) {
        if (cursor >= maxCursor) {
            maxCursor = cursor;
            expectedAtMaxCursor = expected;
        }
    }

    // ---- Call stack for left-recursion detection ----

    public void pushRule(String ruleName) {
        callStack.push(callStackKey(ruleName, cursor));
    }

    public void popRule() {
        callStack.pop();
    }

    /**
     * Detects left recursion: same rule invoked at the same cursor position.
     * Legitimate recursion through terminals (e.g., factor → '(' expr ')') advances
     * the cursor before re-entering, so it is correctly allowed.
     */
    public boolean isInCallStack(String ruleName) {
        long key = callStackKey(ruleName, cursor);
        return callStack.contains(key);
    }

    private static long callStackKey(String ruleName, int position) {
        return ((long) ruleName.hashCode() << 32) | (position & 0xFFFFFFFFL);
    }

    // ---- Memoization ----

    public Long memoKey(String ruleName, int position) {
        return ((long) ruleName.hashCode() << 32) | (position & 0xFFFFFFFFL);
    }

    public ParseMatch getMemo(Long key) {
        return memoTable.get(key);
    }

    public void putMemo(Long key, ParseMatch match) {
        memoTable.put(key, match);
    }

    public boolean hasMemo(Long key) {
        return memoTable.containsKey(key);
    }

    // ---- Save/restore positions ----

    public void mark() {
        marks.push(cursor);
    }

    public void reset() {
        cursor = marks.pop();
    }

    public void commit() {
        marks.pop();
    }

    // ---- Location tracking ----

    public SourceLocation currentLocation() {
        int line = 1;
        int column = 1;
        for (int i = 0; i < cursor && i < input.length(); i++) {
            if (input.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return SourceLocation.of(line, column);
    }

    public SourceLocation locationAt(int offset) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < offset && i < input.length(); i++) {
            if (input.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return SourceLocation.of(line, column);
    }
}
