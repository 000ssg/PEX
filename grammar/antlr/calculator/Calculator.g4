/**
 * Calculator Grammar (ANTLR4)
 * Expression evaluator with operator precedence
 *
 * Demonstrates: alternative labels (# AddSub, # MulDiv), labeled elements
 * (left=expr, right=expr, op=), semantic predicates ({...}?),
 * direct left-recursion handling in ANTLR4.
 */
grammar Calculator;

// ─── Parser Rules ────────────────────────────────────────────────────

program
    : statement+ EOF
    ;

statement
    : assignment NEWLINE?                             # AssignStmt
    | expression NEWLINE?                             # ExprStmt
    | NEWLINE                                         # BlankLine
    ;

// Variable assignment: x = 42
assignment
    : name=ID ASSIGN value=expression
    ;

// Expression with operator precedence via ANTLR4 left-recursion.
// ANTLR resolves precedence by alternative order (first = highest).
expression
    : LPAREN inner=expression RPAREN                  # Parens
    | <assoc=right> base=expression POW exp=expression # Power
    | op=(PLUS | MINUS) operand=expression            # UnaryOp
    | left=expression op=(MUL | DIV | MOD) right=expression # MulDivMod
    | left=expression op=(PLUS | MINUS) right=expression    # AddSub
    | funcName=ID LPAREN args+=expression (COMMA args+=expression)* RPAREN # FuncCall
    | NUMBER                                          # NumberLiteral
    | ID                                              # Variable
    ;

// ─── Lexer Rules ─────────────────────────────────────────────────────

// Numbers: integers and decimals
NUMBER
    : DIGIT+ ('.' DIGIT+)?
    | '.' DIGIT+
    ;

// Identifiers (variable names, function names)
// Semantic predicate example: only allow identifiers <= 32 chars
ID
    : [a-zA-Z_] [a-zA-Z0-9_]* {getText().length() <= 32}?
    ;

// Operators
POW    : '**' ;      // Power (must come before MUL)
MUL    : '*' ;
DIV    : '/' ;
MOD    : '%' ;
PLUS   : '+' ;
MINUS  : '-' ;
ASSIGN : '=' ;

// Delimiters
LPAREN : '(' ;
RPAREN : ')' ;
COMMA  : ',' ;

// Newline is significant (statement separator)
NEWLINE : [\r\n]+ ;

// Whitespace (but not newlines) is skipped
WS : [ \t]+ -> skip ;

// Line comments
LINE_COMMENT : '#' ~[\r\n]* -> skip ;

// ─── Fragment Rules ──────────────────────────────────────────────────

fragment DIGIT : [0-9] ;
