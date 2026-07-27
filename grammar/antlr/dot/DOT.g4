/**
 * DOT (Graphviz) Grammar (ANTLR4)
 * Based on the DOT language specification: https://graphviz.org/doc/info/lang.html
 *
 * Demonstrates: a non-trivial graph description language, optional keywords,
 * labeled elements, multiple alternative forms for edges (-> vs --).
 */
grammar DOT;

// ─── Parser Rules ────────────────────────────────────────────────────

graph
    : STRICT? graphType=(GRAPH | DIGRAPH) name=ID? LBRACE stmtList RBRACE EOF
    ;

stmtList
    : (stmt SEMICOLON?)*
    ;

stmt
    : nodeStmt                                        # NodeStatement
    | edgeStmt                                        # EdgeStatement
    | attrStmt                                        # AttrStatement
    | assignStmt                                      # AssignStatement
    | subgraph                                        # SubgraphStatement
    ;

// Attribute statement: graph [color=red]; node [shape=box];
attrStmt
    : target=(GRAPH | NODE | EDGE) attrList+
    ;

// Assignment: rankdir = LR
assignStmt
    : lhs=ID ASSIGN rhs=ID
    ;

// Edge: a -> b -> c [label="flow"]
edgeStmt
    : source=nodeOrSubgraph edgeRHS+ attrList*
    ;

edgeRHS
    : edgeOp target=nodeOrSubgraph
    ;

edgeOp
    : ARROW                                           // -> (digraph)
    | DASH                                            // -- (graph)
    ;

nodeOrSubgraph
    : nodeId
    | subgraph
    ;

// Node: myNode [label="Hello", shape=box]
nodeStmt
    : nodeId attrList*
    ;

nodeId
    : name=ID port?
    ;

port
    : COLON portName=ID (COLON compass=ID)?
    ;

// Subgraph: subgraph cluster_0 { ... }
subgraph
    : (SUBGRAPH name=ID?)? LBRACE stmtList RBRACE
    ;

// Attribute list: [key=val, key=val]
attrList
    : LBRACKET (attr (COMMA | SEMICOLON)?)* RBRACKET
    ;

attr
    : key=ID ASSIGN value=ID
    ;

// ─── Lexer Rules ─────────────────────────────────────────────────────

// Keywords
STRICT   : [sS] [tT] [rR] [iI] [cC] [tT] ;
GRAPH    : [gG] [rR] [aA] [pP] [hH] ;
DIGRAPH  : [dD] [iI] [gG] [rR] [aA] [pP] [hH] ;
NODE     : [nN] [oO] [dD] [eE] ;
EDGE     : [eE] [dD] [gG] [eE] ;
SUBGRAPH : [sS] [uU] [bB] [gG] [rR] [aA] [pP] [hH] ;

// Edge operators
ARROW : '->' ;
DASH  : '--' ;

// Identifiers: plain, quoted, numeric, or HTML-like
ID
    : ALPHA (ALPHA | DIGIT)*                          // plain identifier
    | '-'? ('.' DIGIT+ | DIGIT+ ('.' DIGIT*)?)       // numeral
    | '"' (~["] | '\\"')* '"'                         // double-quoted string
    ;

// Punctuation
ASSIGN   : '=' ;
LBRACE   : '{' ;
RBRACE   : '}' ;
LBRACKET : '[' ;
RBRACKET : ']' ;
COMMA    : ',' ;
SEMICOLON: ';' ;
COLON    : ':' ;

// Whitespace and comments
WS            : [ \t\r\n]+   -> skip ;
LINE_COMMENT  : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;
HASH_COMMENT  : '#' ~[\r\n]*  -> skip ;

// ─── Fragment Rules ──────────────────────────────────────────────────

fragment ALPHA : [a-zA-Z_-ÿ] ;
fragment DIGIT : [0-9] ;
