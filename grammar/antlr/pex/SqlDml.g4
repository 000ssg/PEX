/**
 * PEX SQL DML Grammar (ANTLR4)
 * Converted from pex-sql-core dml.ebnf (simplified)
 *
 * Demonstrates: labeled alternatives, large rule with many keywords,
 * optional clauses, list patterns with separators.
 */
grammar SqlDml;

// ─── Parser Rules ────────────────────────────────────────────────────

dmlStatement
    : selectStatement                                 # SelectStmt
    | insertStatement                                 # InsertStmt
    | updateStatement                                 # UpdateStmt
    | deleteStatement                                 # DeleteStmt
    ;

// SELECT
selectStatement
    : SELECT DISTINCT? selectList
      (FROM fromClause)?
      (WHERE whereClause)?
      (GROUP BY groupByList)?
      (HAVING havingClause)?
      (ORDER BY orderByList)?
      (LIMIT limitClause)?
    ;

selectList
    : selectItem (COMMA selectItem)*
    ;

selectItem
    : STAR                                            # SelectAll
    | expression (AS? alias)?                         # SelectExpr
    ;

// FROM with joins
fromClause
    : tableRef (COMMA tableRef)*
    ;

tableRef
    : tableName (AS? alias)? joinClause*
    ;

joinClause
    : joinType JOIN tableName (AS? alias)? (ON expression)?
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    ;

whereClause   : expression ;
groupByList   : columnRef (COMMA columnRef)* ;
havingClause  : expression ;

orderByList   : orderByItem (COMMA orderByItem)* ;
orderByItem   : columnRef (ASC | DESC)? (NULLS (FIRST | LAST))? ;

limitClause
    : NUMBER (OFFSET NUMBER)?
    | NUMBER COMMA NUMBER
    ;

// INSERT
insertStatement
    : INSERT INTO tableName (LPAREN columnList RPAREN)?
      (valuesClause | selectStatement)
    ;

valuesClause  : VALUES valueRow (COMMA valueRow)* ;
valueRow      : LPAREN literalValue (COMMA literalValue)* RPAREN ;

// UPDATE
updateStatement
    : UPDATE tableName SET setClause (COMMA setClause)* (WHERE whereClause)?
    ;

setClause
    : columnName ASSIGN literalValue
    ;

// DELETE
deleteStatement
    : DELETE FROM tableName (WHERE whereClause)?
    ;

// Common productions
columnList    : columnName (COMMA columnName)* ;
columnRef     : (tableName DOT)? columnName ;
tableName     : IDENTIFIER ;
columnName    : IDENTIFIER ;
alias         : IDENTIFIER ;

literalValue
    : NUMBER
    | STRING_LITERAL
    | K_NULL
    | K_TRUE
    | K_FALSE
    ;

expression
    : left=expression op=expressionOp right=expression   # BinaryExpr
    | NOT expression                                      # NotExpr
    | functionCall                                        # FuncExpr
    | columnRef                                           # ColRefExpr
    | literalValue                                        # LitExpr
    | LPAREN expression RPAREN                            # ParenExpr
    | STAR                                                # StarExpr
    ;

expressionOp
    : PLUS | MINUS | STAR | SLASH | PERCENT
    | ASSIGN | LT | GT | LTE | GTE | LTGT | NEQ
    | AND | OR | LIKE | IS | IN | CONCAT
    ;

functionCall
    : IDENTIFIER LPAREN (expression (COMMA expression)* | STAR)? RPAREN
    ;

// ─── Keywords ────────────────────────────────────────────────────────

SELECT   : S E L E C T ;
DISTINCT : D I S T I N C T ;
FROM     : F R O M ;
WHERE    : W H E R E ;
GROUP    : G R O U P ;
BY       : B Y ;
HAVING   : H A V I N G ;
ORDER    : O R D E R ;
LIMIT    : L I M I T ;
OFFSET   : O F F S E T ;
INSERT   : I N S E R T ;
INTO     : I N T O ;
VALUES   : V A L U E S ;
UPDATE   : U P D A T E ;
SET      : S E T ;
DELETE   : D E L E T E ;
JOIN     : J O I N ;
ON       : O N ;
INNER    : I N N E R ;
LEFT     : L E F T ;
RIGHT    : R I G H T ;
FULL     : F U L L ;
OUTER    : O U T E R ;
CROSS    : C R O S S ;
ASC      : A S C ;
DESC     : D E S C ;
NULLS    : N U L L S ;
FIRST    : F I R S T ;
LAST     : L A S T ;
AS       : A S ;
AND      : A N D ;
OR       : O R ;
NOT      : N O T ;
LIKE     : L I K E ;
IS       : I S ;
IN       : I N ;
K_NULL   : N U L L ;
K_TRUE   : T R U E ;
K_FALSE  : F A L S E ;

// ─── Lexer Rules ─────────────────────────────────────────────────────

NUMBER         : [0-9]+ ;
STRING_LITERAL : '\'' ~[']* '\'' ;
IDENTIFIER     : [a-zA-Z_] [a-zA-Z0-9_]* ;

// Operators and punctuation
ASSIGN : '=' ;
LT     : '<' ;
GT     : '>' ;
LTE    : '<=' ;
GTE    : '>=' ;
LTGT   : '<>' ;
NEQ    : '!=' ;
PLUS   : '+' ;
MINUS  : '-' ;
STAR   : '*' ;
SLASH  : '/' ;
PERCENT: '%' ;
CONCAT : '||' ;
DOT    : '.' ;
COMMA  : ',' ;
LPAREN : '(' ;
RPAREN : ')' ;

WS : [ \t\r\n]+ -> skip ;

// ─── Case-Insensitive Keyword Fragments ──────────────────────────────

fragment A : [aA]; fragment B : [bB]; fragment C : [cC]; fragment D : [dD];
fragment E : [eE]; fragment F : [fF]; fragment G : [gG]; fragment H : [hH];
fragment I : [iI]; fragment J : [jJ]; fragment K : [kK]; fragment L : [lL];
fragment M : [mM]; fragment N : [nN]; fragment O : [oO]; fragment P : [pP];
fragment Q : [qQ]; fragment R : [rR]; fragment S : [sS]; fragment T : [tT];
fragment U : [uU]; fragment V : [vV]; fragment W : [wW]; fragment X : [xX];
fragment Y : [yY]; fragment Z : [zZ];
