/**
 * Simple SQL SELECT Grammar (ANTLR4)
 * A standalone SQL grammar for SELECT queries with common clauses
 *
 * Demonstrates: large grammar with many alternatives, case-insensitive keywords
 * via fragment rules, labeled elements, real-world SQL patterns.
 */
grammar SimpleSQL;

// ─── Parser Rules ────────────────────────────────────────────────────

query
    : selectStatement SEMICOLON? EOF
    ;

selectStatement
    : SELECT (ALL | DISTINCT)? selectElements
      fromClause?
      whereClause?
      groupByClause?
      havingClause?
      orderByClause?
      limitClause?
    ;

selectElements
    : selectElement (COMMA selectElement)*
    ;

selectElement
    : STAR                                            # SelectStar
    | table=ID DOT STAR                               # SelectTableStar
    | expr=expression (AS? alias=ID)?                 # SelectExpression
    ;

// FROM clause
fromClause
    : FROM tableSource (COMMA tableSource)*
    ;

tableSource
    : tableName (AS? alias=ID)? joinPart*
    ;

joinPart
    : joinType? JOIN tableName (AS? alias=ID)? (ON condition=expression)? # JoinClause
    | NATURAL joinType? JOIN tableName (AS? alias=ID)?                     # NaturalJoin
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    ;

tableName
    : schema=ID DOT name=ID
    | name=ID
    ;

// WHERE clause
whereClause
    : WHERE expression
    ;

// GROUP BY clause
groupByClause
    : GROUP BY expression (COMMA expression)*
    ;

// HAVING clause
havingClause
    : HAVING expression
    ;

// ORDER BY clause
orderByClause
    : ORDER BY orderByElement (COMMA orderByElement)*
    ;

orderByElement
    : expression order=(ASC | DESC)? (NULLS nullOrder=(FIRST | LAST))?
    ;

// LIMIT clause
limitClause
    : LIMIT count=INTEGER (OFFSET offset=INTEGER)?
    ;

// Expressions
expression
    : LPAREN expression RPAREN                        # ParenExpr
    | expression IS NOT? NULL                         # IsNullExpr
    | expression NOT? BETWEEN lo=expression AND hi=expression # BetweenExpr
    | expression NOT? IN LPAREN (expression (COMMA expression)*)? RPAREN # InExpr
    | expression NOT? LIKE pattern=STRING             # LikeExpr
    | left=expression op=compOp right=expression      # ComparisonExpr
    | left=expression AND right=expression            # AndExpr
    | left=expression OR right=expression             # OrExpr
    | NOT expression                                  # NotExpr
    | funcName=ID LPAREN (DISTINCT? expression (COMMA expression)* | STAR)? RPAREN # FunctionExpr
    | table=ID DOT column=ID                          # QualifiedColumn
    | ID                                              # ColumnExpr
    | INTEGER                                         # IntLiteral
    | DECIMAL                                         # DecimalLiteral
    | STRING                                          # StringLiteral
    | NULL                                            # NullLiteral
    ;

compOp
    : EQ | NEQ | NEQ2 | LT | GT | LTE | GTE
    ;

// ─── Keywords (case-insensitive via fragments) ───────────────────────

SELECT   : S E L E C T ;     ALL      : A L L ;
DISTINCT : D I S T I N C T ; FROM     : F R O M ;
WHERE    : W H E R E ;       GROUP    : G R O U P ;
BY       : B Y ;             HAVING   : H A V I N G ;
ORDER    : O R D E R ;       ASC      : A S C ;
DESC     : D E S C ;         LIMIT    : L I M I T ;
OFFSET   : O F F S E T ;     AS       : A S ;
JOIN     : J O I N ;         ON       : O N ;
INNER    : I N N E R ;       LEFT     : L E F T ;
RIGHT    : R I G H T ;       FULL     : F U L L ;
OUTER    : O U T E R ;       CROSS    : C R O S S ;
NATURAL  : N A T U R A L ;   AND      : A N D ;
OR       : O R ;             NOT      : N O T ;
IN       : I N ;             BETWEEN  : B E T W E E N ;
LIKE     : L I K E ;         IS       : I S ;
NULL     : N U L L ;         NULLS    : N U L L S ;
FIRST    : F I R S T ;       LAST     : L A S T ;

// ─── Lexer Rules ─────────────────────────────────────────────────────

DECIMAL  : DIGIT+ '.' DIGIT+ ;
INTEGER  : DIGIT+ ;
STRING   : '\'' (~['] | '\'\'')* '\'' ;
ID       : [a-zA-Z_] [a-zA-Z0-9_]* ;

// Operators
EQ   : '=' ;  NEQ  : '!=' ; NEQ2 : '<>' ;
LT   : '<' ;  GT   : '>' ;
LTE  : '<=' ; GTE  : '>=' ;
STAR : '*' ;  DOT  : '.' ;
COMMA: ',' ;  SEMICOLON : ';' ;
LPAREN : '(' ; RPAREN : ')' ;

WS           : [ \t\r\n]+ -> skip ;
LINE_COMMENT : '--' ~[\r\n]* -> skip ;
BLOCK_COMMENT: '/*' .*? '*/' -> skip ;

// ─── Case-insensitive fragments ──────────────────────────────────────

fragment DIGIT : [0-9] ;
fragment A : [aA]; fragment B : [bB]; fragment C : [cC]; fragment D : [dD];
fragment E : [eE]; fragment F : [fF]; fragment G : [gG]; fragment H : [hH];
fragment I : [iI]; fragment J : [jJ]; fragment K : [kK]; fragment L : [lL];
fragment M : [mM]; fragment N : [nN]; fragment O : [oO]; fragment P : [pP];
fragment Q : [qQ]; fragment R : [rR]; fragment S : [sS]; fragment T : [tT];
fragment U : [uU]; fragment V : [vV]; fragment W : [wW]; fragment X : [xX];
fragment Y : [yY]; fragment Z : [zZ];
