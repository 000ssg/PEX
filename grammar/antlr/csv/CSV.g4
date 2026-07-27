/**
 * CSV Grammar (ANTLR4)
 * Parses RFC 4180-style CSV files
 *
 * Demonstrates: simple parser/lexer separation, fragment rules,
 * negation ~ for character exclusion, -> skip for ignorable tokens.
 */
grammar CSV;

// ─── Parser Rules ────────────────────────────────────────────────────

file
    : header row* EOF
    ;

header
    : row
    ;

row
    : field (COMMA field)* (NEWLINE | EOF)
    ;

field
    : QUOTED_FIELD                                    # QuotedField
    | UNQUOTED_FIELD                                  # UnquotedField
    |                                                 # EmptyField
    ;

// ─── Lexer Rules ─────────────────────────────────────────────────────

// Comma separator
COMMA : ',' ;

// Newline (handles CR, LF, and CRLF)
NEWLINE : '\r'? '\n' | '\r' ;

// Quoted field: handles escaped quotes ("") inside
QUOTED_FIELD
    : '"' (ESCAPED_QUOTE | SAFECODEPOINT)* '"'
    ;

// Unquoted field: everything except comma, quote, newline
UNQUOTED_FIELD
    : ~[,"\r\n]+
    ;

// ─── Fragment Rules ──────────────────────────────────────────────────

// Escaped double-quote inside a quoted field
fragment ESCAPED_QUOTE
    : '""'
    ;

// Any character except double-quote (used inside quoted fields)
fragment SAFECODEPOINT
    : ~["]
    ;
