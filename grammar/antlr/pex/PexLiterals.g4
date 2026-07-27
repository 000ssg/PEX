/**
 * PEX Literals Grammar (ANTLR4)
 * Converted from pex-base literals.ebnf
 *
 * Demonstrates: fragment rules for reusable character patterns,
 * character classes [a-z], negation ~, lexer-only grammar patterns.
 */
grammar PexLiterals;

// ─── Parser Rules ────────────────────────────────────────────────────

literal
    : floatLiteral
    | hexLiteral
    | binLiteral
    | octLiteral
    | intLiteral
    | boolLiteral
    | stringLiteral
    | nullLiteral
    ;

floatLiteral   : FLOAT_LITERAL ;
hexLiteral     : HEX_LITERAL ;
binLiteral     : BIN_LITERAL ;
octLiteral     : OCT_LITERAL ;
intLiteral     : INT_LITERAL ;
boolLiteral    : BOOL_LITERAL ;
stringLiteral  : STRING_LITERAL ;
nullLiteral    : NULL_LITERAL ;

identifier     : IDENTIFIER ;

// ─── Lexer Rules ─────────────────────────────────────────────────────

// Keywords (must come before IDENTIFIER)
NULL_LITERAL : 'null' ;
BOOL_LITERAL : 'true' | 'false' ;

// Floating-point: 3.14, .5, 1e10, 2.5e-3
FLOAT_LITERAL
    : DIGIT+ '.' DIGIT* EXPONENT?
    | '.' DIGIT+ EXPONENT?
    | DIGIT+ EXPONENT
    ;

// Radix literals
HEX_LITERAL : '0' [xX] HEX_DIGIT+ ;
BIN_LITERAL : '0' [bB] [01]+ ;
OCT_LITERAL : '0' [oO] [0-7]+ ;

// Plain integer
INT_LITERAL : DIGIT+ ;

// String: double-quoted or single-quoted (no escapes in simplified version)
STRING_LITERAL
    : '"' ~["\r\n]* '"'
    | '\'' ~['\r\n]* '\''
    ;

// Identifiers: start with letter or underscore
IDENTIFIER : LETTER (LETTER | DIGIT)* ;

// Skip whitespace
WS : [ \t\r\n]+ -> skip ;

// ─── Fragment Rules ──────────────────────────────────────────────────
// Fragments are reusable building blocks that never produce tokens themselves.

fragment DIGIT     : [0-9] ;
fragment HEX_DIGIT : [0-9a-fA-F] ;
fragment LETTER    : [a-zA-Z_] ;
fragment EXPONENT  : [eE] [+\-]? DIGIT+ ;
