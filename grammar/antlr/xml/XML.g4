/**
 * Simplified XML Grammar (ANTLR4)
 *
 * Demonstrates: LEXER MODES — the key ANTLR feature for context-sensitive tokenization.
 * When we see '<', we switch to INSIDE mode where '>' and attribute syntax are recognized.
 * This is impossible to express in pure BNF.
 *
 * Features shown: mode(), pushMode(), popMode(), -> skip, -> channel(HIDDEN)
 */
grammar XML;

// ─── Parser Rules ────────────────────────────────────────────────────

document
    : prolog? element EOF
    ;

prolog
    : PI_OPEN attribute* PI_CLOSE
    ;

element
    : OPEN name=Name attribute* CLOSE content* OPEN_SLASH Name CLOSE  # FullElement
    | OPEN name=Name attribute* SLASH_CLOSE                            # SelfClosing
    ;

content
    : element                                         # ContentElement
    | TEXT                                             # ContentText
    | CDATA                                           # ContentCData
    | COMMENT                                         # ContentComment
    ;

attribute
    : Name EQUALS STRING
    ;

// ─── Default Mode (outside tags) ────────────────────────────────────

// Comments: <!-- ... -->
COMMENT    : '<!--' .*? '-->' ;

// CDATA sections: <![CDATA[ ... ]]>
CDATA      : '<![CDATA[' .*? ']]>' ;

// Processing instruction markers
PI_OPEN    : '<?' -> pushMode(INSIDE) ;

// Tag openers
OPEN_SLASH : '</' -> pushMode(INSIDE) ;
OPEN       : '<'  -> pushMode(INSIDE) ;

// Text content between tags (everything that's not a '<')
TEXT       : ~[<]+ ;

// ─── INSIDE Mode (inside a tag) ─────────────────────────────────────
// This mode is active after we see '<' and until we see '>'

mode INSIDE;

// Close tag and return to default mode
CLOSE       : '>'  -> popMode ;
SLASH_CLOSE : '/>' -> popMode ;
PI_CLOSE    : '?>' -> popMode ;

// Attribute value strings
STRING      : '"' ~["]* '"'
            | '\'' ~[']* '\''
            ;

EQUALS      : '=' ;

// Tag/attribute names
Name        : NAMESTART NAMECHAR* ;

// Whitespace inside tags is skipped
S           : [ \t\r\n]+ -> skip ;

// ─── Fragment Rules ──────────────────────────────────────────────────

fragment NAMESTART : [a-zA-Z_:] ;
fragment NAMECHAR  : NAMESTART | [0-9\-.] ;
