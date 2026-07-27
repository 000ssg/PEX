package ssg.pex.bnf.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.Group;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.RepetitionKind;
import ssg.pex.bnf.model.RuleExpression;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;
import ssg.pex.result.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A recursive-descent parser engine that interprets a {@link Grammar} to parse input text.
 * Features packrat memoization and left-recursion detection.
 */
public final class RecursiveDescentEngine {

    private static final Logger log = LoggerFactory.getLogger(RecursiveDescentEngine.class);

    public Result<ParseMatch> parse(String input, Grammar grammar) {
        var ctx = new ParseContext(input, grammar);

        log.debug("Starting parse with grammar '{}', start rule '{}', input length {}",
                grammar.name(), grammar.startRuleName(), input.length());

        var result = parseRule(grammar.startRuleName(), ctx);
        if (result == null) {
            return buildError(ctx);
        }

        // Check that all input was consumed
        skipWhitespace(ctx);
        if (!ctx.isAtEnd()) {
            return buildError(ctx);
        }

        log.debug("Parse succeeded");
        return Result.success(result);
    }

    public ParseMatch parseRule(String ruleName, ParseContext ctx) {
        // Check memoization
        var memoKey = ctx.memoKey(ruleName, ctx.cursor());
        if (ctx.hasMemo(memoKey)) {
            var cached = ctx.getMemo(memoKey);
            if (cached != null) {
                ctx.setCursor(ctx.cursor() + cached.matchedText().length());
            }
            return cached;
        }

        // Left-recursion detection
        if (ctx.isInCallStack(ruleName)) {
            log.trace("Left recursion detected for rule '{}' at position {}", ruleName, ctx.cursor());
            return null;
        }

        var rule = ctx.grammar().rule(ruleName).orElse(null);
        if (rule == null) {
            ctx.recordExpected("rule '" + ruleName + "'");
            log.warn("Undefined rule referenced: '{}'", ruleName);
            return null;
        }

        ctx.pushRule(ruleName);
        int startPos = ctx.cursor();

        try {
            var match = parseExpression(rule.body(), ctx);
            if (match == null) {
                ctx.setCursor(startPos);
                ctx.putMemo(memoKey, null);
                return null;
            }

            var text = ctx.input().substring(startPos, ctx.cursor());
            var ruleMatch = ParseMatch.node(ruleName, text, ctx.locationAt(startPos),
                    match.children().isEmpty() && match.ruleName() == null
                            ? List.of(match)
                            : List.of(match));
            ctx.putMemo(memoKey, ruleMatch);
            return ruleMatch;
        } finally {
            ctx.popRule();
        }
    }

    public ParseMatch parseExpression(RuleExpression expr, ParseContext ctx) {
        return switch (expr) {
            case Sequence seq -> parseSequence(seq, ctx);
            case Alternation alt -> parseAlternation(alt, ctx);
            case Repetition rep -> parseRepetition(rep, ctx);
            case Terminal term -> parseTerminal(term, ctx);
            case NonTerminal nt -> parseNonTerminal(nt, ctx);
            case Group grp -> parseGroup(grp, ctx);
        };
    }

    private ParseMatch parseSequence(Sequence seq, ParseContext ctx) {
        int startPos = ctx.cursor();
        var children = new ArrayList<ParseMatch>();

        for (var element : seq.elements()) {
            skipWhitespace(ctx);
            var match = parseExpression(element, ctx);
            if (match == null) {
                ctx.setCursor(startPos);
                return null;
            }
            children.add(match);
        }

        var text = ctx.input().substring(startPos, ctx.cursor());
        return ParseMatch.node(null, text, ctx.locationAt(startPos), children);
    }

    private ParseMatch parseAlternation(Alternation alt, ParseContext ctx) {
        int startPos = ctx.cursor();

        for (var alternative : alt.alternatives()) {
            ctx.setCursor(startPos);
            var match = parseExpression(alternative, ctx);
            if (match != null) {
                return match;
            }
        }

        ctx.setCursor(startPos);
        return null;
    }

    private ParseMatch parseRepetition(Repetition rep, ParseContext ctx) {
        int startPos = ctx.cursor();
        var children = new ArrayList<ParseMatch>();

        // For ONE_OR_MORE, we need at least one match
        if (rep.kind() == RepetitionKind.ONE_OR_MORE) {
            skipWhitespace(ctx);
            var first = parseExpression(rep.body(), ctx);
            if (first == null) {
                ctx.setCursor(startPos);
                return null;
            }
            children.add(first);
        }

        // For OPTIONAL, try once
        if (rep.kind() == RepetitionKind.OPTIONAL) {
            skipWhitespace(ctx);
            var match = parseExpression(rep.body(), ctx);
            if (match != null) {
                children.add(match);
            }
            var text = ctx.input().substring(startPos, ctx.cursor());
            return ParseMatch.node(null, text, ctx.locationAt(startPos), children);
        }

        // Collect remaining matches (for ZERO_OR_MORE and ONE_OR_MORE)
        while (!ctx.isAtEnd()) {
            int beforeAttempt = ctx.cursor();
            skipWhitespace(ctx);
            var match = parseExpression(rep.body(), ctx);
            if (match == null) {
                ctx.setCursor(beforeAttempt);
                break;
            }
            // Guard against zero-length matches causing infinite loops
            if (ctx.cursor() == beforeAttempt) {
                break;
            }
            children.add(match);
        }

        var text = ctx.input().substring(startPos, ctx.cursor());
        return ParseMatch.node(null, text, ctx.locationAt(startPos), children);
    }

    private ParseMatch parseTerminal(Terminal term, ParseContext ctx) {
        skipWhitespace(ctx);
        int startPos = ctx.cursor();

        if (term.isRegex()) {
            return parseRegexTerminal(term, ctx, startPos);
        }

        // Literal match
        var literal = term.value();
        if (startPos + literal.length() > ctx.input().length()) {
            ctx.recordExpected("'" + literal + "'");
            return null;
        }

        for (int i = 0; i < literal.length(); i++) {
            if (ctx.input().charAt(startPos + i) != literal.charAt(i)) {
                ctx.recordExpected("'" + literal + "'");
                return null;
            }
        }

        ctx.setCursor(startPos + literal.length());
        return ParseMatch.leaf(null, literal, ctx.locationAt(startPos));
    }

    private ParseMatch parseRegexTerminal(Terminal term, ParseContext ctx, int startPos) {
        var pattern = Pattern.compile(term.value());
        var matcher = pattern.matcher(ctx.remaining());
        if (matcher.lookingAt()) {
            var matched = matcher.group();
            ctx.setCursor(startPos + matched.length());
            return ParseMatch.leaf(null, matched, ctx.locationAt(startPos));
        }
        ctx.recordExpected("pattern /" + term.value() + "/");
        return null;
    }

    private ParseMatch parseNonTerminal(NonTerminal nt, ParseContext ctx) {
        skipWhitespace(ctx);
        return parseRule(nt.ruleName(), ctx);
    }

    private ParseMatch parseGroup(Group grp, ParseContext ctx) {
        return parseExpression(grp.inner(), ctx);
    }

    private void skipWhitespace(ParseContext ctx) {
        while (!ctx.isAtEnd() && Character.isWhitespace(ctx.peek())) {
            ctx.advance();
        }
    }

    private Result<ParseMatch> buildError(ParseContext ctx) {
        var location = ctx.locationAt(ctx.maxCursor());
        var remaining = ctx.input().substring(
                Math.min(ctx.maxCursor(), ctx.input().length()),
                Math.min(ctx.maxCursor() + 20, ctx.input().length()));

        var message = new StringBuilder("Parse failed at ").append(location);
        if (ctx.expectedAtMaxCursor() != null) {
            message.append(": expected ").append(ctx.expectedAtMaxCursor());
        }
        if (!remaining.isEmpty()) {
            message.append(", found '").append(remaining).append("'");
        }

        return Result.failure("PARSE_FAILED", message.toString(), location);
    }
}
