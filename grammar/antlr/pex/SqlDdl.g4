/**
 * PEX SQL DDL Grammar (ANTLR4)
 * Converted from pex-sql-core ddl.ebnf (simplified)
 *
 * Demonstrates: complex rule hierarchies, keyword-heavy grammars,
 * optional sequences, repeated clauses with constraints.
 */
grammar SqlDdl;

// ─── Parser Rules ────────────────────────────────────────────────────

ddlStatement
    : createTable                                     # CreateTableStmt
    | createIndex                                     # CreateIndexStmt
    | createView                                      # CreateViewStmt
    | alterTable                                      # AlterTableStmt
    | dropTable                                       # DropTableStmt
    ;

// CREATE TABLE (including TEMP/TEMPORARY/GLOBAL TEMPORARY variants)
createTable
    : CREATE (GLOBAL TEMPORARY | TEMPORARY | TEMP)? TABLE (IF NOT EXISTS)? tableName
      LPAREN tableElement (COMMA tableElement)* RPAREN
      (ON COMMIT (DELETE | PRESERVE) ROWS)?
    ;

tableElement
    : columnDef                                       # ColumnElement
    | tableConstraint                                 # ConstraintElement
    ;

columnDef
    : columnName dataType columnConstraint*
    ;

columnConstraint
    : NOT NULL                                        # NotNullConstraint
    | NULL                                            # NullableConstraint
    | DEFAULT literalValue                            # DefaultConstraint
    | AUTO_INCREMENT                                  # AutoIncConstraint
    | PRIMARY KEY                                     # PkColumnConstraint
    | UNIQUE                                          # UniqueColumnConstraint
    | REFERENCES tableName (LPAREN columnList RPAREN)? # FkColumnConstraint
    ;

tableConstraint
    : (CONSTRAINT constraintName)?
      ( PRIMARY KEY LPAREN columnList RPAREN          # PrimaryKeyConstraint
      | FOREIGN KEY LPAREN columnList RPAREN
        REFERENCES tableName LPAREN columnList RPAREN
        (ON (DELETE | UPDATE) referentialAction)*      # ForeignKeyConstraint
      | UNIQUE LPAREN columnList RPAREN               # UniqueTableConstraint
      | CHECK LPAREN expression RPAREN                # CheckConstraint
      )
    ;

referentialAction
    : CASCADE
    | SET NULL
    | RESTRICT
    | NO ACTION
    ;

// CREATE INDEX
createIndex
    : CREATE UNIQUE? INDEX indexName ON tableName LPAREN columnList RPAREN
    ;

// CREATE VIEW
createView
    : CREATE (OR REPLACE)? VIEW viewName AS selectStatement
    ;

// ALTER TABLE
alterTable
    : ALTER TABLE tableName alterAction (COMMA alterAction)*
    ;

alterAction
    : ADD COLUMN? columnDef                           # AddColumnAction
    | DROP COLUMN? columnName                         # DropColumnAction
    | RENAME COLUMN? columnName TO columnName         # RenameColumnAction
    | ADD tableConstraint                             # AddConstraintAction
    ;

// DROP TABLE
dropTable
    : DROP TABLE (IF EXISTS)? tableName
    ;

// Data types
dataType
    : INT | INTEGER | BIGINT
    | FLOAT | REAL | DOUBLE
    | DECIMAL (LPAREN NUMBER (COMMA NUMBER)? RPAREN)?
    | NUMERIC
    | VARCHAR (LPAREN NUMBER RPAREN)?
    | CHAR | TEXT
    | BOOLEAN | BOOL
    | DATE | TIMESTAMP | DATETIME
    | BLOB | BYTEA
    | SERIAL
    ;

// Simplified SELECT for CREATE VIEW
selectStatement
    : SELECT selectItem (COMMA selectItem)*
      (FROM IDENTIFIER (COMMA IDENTIFIER)*)?
      (WHERE expression)?
    ;

selectItem
    : STAR
    | expression (AS IDENTIFIER)?
    ;

// Common
columnList     : columnName (COMMA columnName)* ;
tableName      : IDENTIFIER ;
columnName     : IDENTIFIER ;
indexName       : IDENTIFIER ;
viewName       : IDENTIFIER ;
constraintName : IDENTIFIER ;

literalValue : NUMBER | STRING_LITERAL | NULL | K_TRUE | K_FALSE ;

expression
    : expression op=(PLUS | MINUS | STAR | SLASH | EQ | LT | GT | LTE | GTE | LTGT | NEQ) expression
    | expression (AND | OR) expression
    | NOT expression
    | IDENTIFIER
    | literalValue
    | LPAREN expression RPAREN
    ;

// ─── Keywords ────────────────────────────────────────────────────────

CREATE   : C R E A T E ;   TABLE    : T A B L E ;
ALTER    : A L T E R ;     DROP     : D R O P ;
INDEX    : I N D E X ;     VIEW     : V I E W ;
IF       : I F ;           NOT      : N O T ;
EXISTS   : E X I S T S ;   NULL     : N U L L ;
DEFAULT  : D E F A U L T ; PRIMARY  : P R I M A R Y ;
KEY      : K E Y ;         UNIQUE   : U N I Q U E ;
FOREIGN  : F O R E I G N ; REFERENCES : R E F E R E N C E S ;
CONSTRAINT : C O N S T R A I N T ;
TEMP     : T E M P ;       TEMPORARY : T E M P O R A R Y ;
GLOBAL   : G L O B A L ;   PRESERVE : P R E S E R V E ;
COMMIT   : C O M M I T ;   ROWS     : R O W S ;
CHECK    : C H E C K ;     ON       : O N ;
DELETE   : D E L E T E ;   UPDATE   : U P D A T E ;
CASCADE  : C A S C A D E ; SET      : S E T ;
RESTRICT : R E S T R I C T ; NO     : N O ;
ACTION   : A C T I O N ;   ADD      : A D D ;
COLUMN   : C O L U M N ;   RENAME   : R E N A M E ;
TO       : T O ;           REPLACE  : R E P L A C E ;
OR       : O R ;           AS       : A S ;
AND      : A N D ;         SELECT   : S E L E C T ;
FROM     : F R O M ;       WHERE    : W H E R E ;
AUTO_INCREMENT : A U T O '_' I N C R E M E N T ;

// Data type keywords
INT      : I N T ;         INTEGER  : I N T E G E R ;
BIGINT   : B I G I N T ;   FLOAT    : F L O A T ;
REAL     : R E A L ;       DOUBLE   : D O U B L E ;
DECIMAL  : D E C I M A L ; NUMERIC  : N U M E R I C ;
VARCHAR  : V A R C H A R ; CHAR     : C H A R ;
TEXT     : T E X T ;       BOOLEAN  : B O O L E A N ;
BOOL     : B O O L ;       DATE     : D A T E ;
TIMESTAMP: T I M E S T A M P ;
DATETIME : D A T E T I M E ;
BLOB     : B L O B ;       BYTEA    : B Y T E A ;
SERIAL   : S E R I A L ;
K_TRUE   : T R U E ;       K_FALSE  : F A L S E ;

// ─── Lexer Rules ─────────────────────────────────────────────────────

NUMBER         : [0-9]+ ;
STRING_LITERAL : '\'' ~[']* '\'' ;
IDENTIFIER     : [a-zA-Z_] [a-zA-Z0-9_]* ;

EQ   : '=' ;  LT   : '<' ;  GT   : '>' ;
LTE  : '<=' ; GTE  : '>=' ; LTGT : '<>' ; NEQ : '!=' ;
PLUS : '+' ;  MINUS: '-' ;  STAR : '*' ;  SLASH: '/' ;
DOT  : '.' ;  COMMA: ',' ;  LPAREN: '(' ; RPAREN: ')' ;

WS : [ \t\r\n]+ -> skip ;

// ─── Fragments for case-insensitive keywords ─────────────────────────

fragment A : [aA]; fragment B : [bB]; fragment C : [cC]; fragment D : [dD];
fragment E : [eE]; fragment F : [fF]; fragment G : [gG]; fragment H : [hH];
fragment I : [iI]; fragment J : [jJ]; fragment K : [kK]; fragment L : [lL];
fragment M : [mM]; fragment N : [nN]; fragment O : [oO]; fragment P : [pP];
fragment Q : [qQ]; fragment R : [rR]; fragment S : [sS]; fragment T : [tT];
fragment U : [uU]; fragment V : [vV]; fragment W : [wW]; fragment X : [xX];
fragment Y : [yY]; fragment Z : [zZ];
