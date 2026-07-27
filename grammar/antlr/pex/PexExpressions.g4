/**
 * PEX Expressions Grammar (ANTLR4)
 * Converted from pex-base expressions.ebnf
 *
 * Demonstrates: operator precedence via rule hierarchy, labeled alternatives,
 * labeled elements, character classes, fragment rules.
 */
grammar PexExpressions;

// ─── Parser Rules ────────────────────────────────────────────────────

program
    : statement+ EOF
    ;

statement
    : assignment
    | conditional
    | loop
    | returnStatement
    | expression
    ;

block
    : LBRACE statement* RBRACE
    ;

assignment
    : IDENTIFIER ASSIGN expression
    ;

// Control flow
conditional
    : IF LPAREN expression RPAREN block (ELSE block)?
    ;

loop
    : WHILE LPAREN expression RPAREN block
    ;

returnStatement
    : RETURN expression?
    ;

// Expression precedence (lowest to highest)
expression
    : orExpr
    ;

orExpr
    : andExpr (OR andExpr)*                         # LogicalOr
    ;

andExpr
    : bitwiseOrExpr (AND bitwiseOrExpr)*             # LogicalAnd
    ;

bitwiseOrExpr
    : bitwiseXorExpr (PIPE bitwiseXorExpr)*          # BitOr
    ;

bitwiseXorExpr
    : bitwiseAndExpr (CARET bitwiseAndExpr)*         # BitXor
    ;

bitwiseAndExpr
    : equalityExpr (AMP equalityExpr)*               # BitAnd
    ;

equalityExpr
    : relationalExpr (op=(EQ | NEQ) relationalExpr)* # Equality
    ;

relationalExpr
    : shiftExpr (op=(LTE | GTE | LT | GT) shiftExpr)* # Relational
    ;

shiftExpr
    : additionExpr (op=(UNSIGNED_RIGHT_SHIFT | LEFT_SHIFT | RIGHT_SHIFT) additionExpr)* # Shift
    ;

additionExpr
    : multiplicationExpr (op=(PLUS | MINUS) multiplicationExpr)* # AddSub
    ;

multiplicationExpr
    : unaryExpr (op=(STAR | SLASH | PERCENT) unaryExpr)* # MulDivMod
    ;

unaryExpr
    : op=(BANG | MINUS | TILDE | PLUS) unaryExpr     # UnaryOp
    | primary                                         # UnaryPrimary
    ;

primary
    : literal                                         # LiteralExpr
    | functionCall                                    # FuncCallExpr
    | IDENTIFIER LBRACKET expression RBRACKET         # IndexAccess
    | IDENTIFIER                                      # IdentifierExpr
    | LPAREN expression RPAREN                        # ParenExpr
    ;

functionCall
    : IDENTIFIER LPAREN (expression (COMMA expression)*)? RPAREN
    ;

literal
    : FLOAT_LITERAL
    | HEX_LITERAL
    | BIN_LITERAL
    | OCT_LITERAL
    | INT_LITERAL
    | BOOL_LITERAL
    | STRING_LITERAL
    | NULL_LITERAL
    ;

// ─── Lexer Rules ─────────────────────────────────────────────────────

// Keywords
IF       : 'if';
ELSE     : 'else';
WHILE    : 'while';
RETURN   : 'return';
NULL_LITERAL : 'null';
BOOL_LITERAL : 'true' | 'false';

// Numeric literals
FLOAT_LITERAL : DIGITS '.' DIGITS? EXPONENT?
              | '.' DIGITS EXPONENT?
              | DIGITS EXPONENT ;
HEX_LITERAL   : '0' [xX] [0-9a-fA-F]+ ;
BIN_LITERAL   : '0' [bB] [01]+ ;
OCT_LITERAL   : '0' [oO] [0-7]+ ;
INT_LITERAL   : DIGITS ;

// String literal
STRING_LITERAL : '"' ~["]* '"'
               | '\'' ~[']* '\'' ;

// Identifier
IDENTIFIER : [a-zA-Z_] [a-zA-Z0-9_]* ;

// Operators
OR        : '||';
AND       : '&&';
PIPE      : '|';
CARET     : '^';
AMP       : '&';
EQ        : '==';
NEQ       : '!=';
LTE       : '<=';
GTE       : '>=';
LT        : '<';
GT        : '>';
UNSIGNED_RIGHT_SHIFT : '>>>';
LEFT_SHIFT  : '<<';
RIGHT_SHIFT : '>>';
PLUS      : '+';
MINUS     : '-';
STAR      : '*';
SLASH     : '/';
PERCENT   : '%';
BANG      : '!';
TILDE     : '~';
ASSIGN    : '=';

// Delimiters
LPAREN    : '(';
RPAREN    : ')';
LBRACE    : '{';
RBRACE    : '}';
LBRACKET  : '[';
RBRACKET  : ']';
COMMA     : ',';

// Whitespace and comments -> skip
WS            : [ \t\r\n]+ -> skip ;
LINE_COMMENT  : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;

// ─── Fragments ───────────────────────────────────────────────────────

fragment DIGITS   : [0-9]+ ;
fragment EXPONENT : [eE] [+\-]? DIGITS ;
