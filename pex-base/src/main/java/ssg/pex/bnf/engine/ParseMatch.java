package ssg.pex.bnf.engine;

import ssg.pex.ast.SourceLocation;

import java.util.List;

public record ParseMatch(String ruleName,
                         String matchedText,
                         SourceLocation location,
                         List<ParseMatch> children) {

    public ParseMatch {
        children = List.copyOf(children);
    }

    public static ParseMatch leaf(String ruleName, String matchedText, SourceLocation location) {
        return new ParseMatch(ruleName, matchedText, location, List.of());
    }

    public static ParseMatch node(String ruleName, String matchedText,
                                  SourceLocation location, List<ParseMatch> children) {
        return new ParseMatch(ruleName, matchedText, location, children);
    }
}
