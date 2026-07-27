/**
 * Simple Markdown Grammar (ANTLR4)
 * A simplified Markdown parser covering headings, emphasis, links, code, and lists
 *
 * Demonstrates: character-level parsing, nested inline formatting,
 * -> channel(HIDDEN) for preserving but hiding tokens, negation ~.
 */
grammar SimpleMarkdown;

// ─── Parser Rules ────────────────────────────────────────────────────

document
    : block+ EOF
    ;

block
    : heading
    | codeBlock
    | unorderedList
    | orderedList
    | horizontalRule
    | blockquote
    | paragraph
    | BLANK_LINE
    ;

// Headings: # H1, ## H2, etc.
heading
    : HASH+ inline+ NEWLINE?
    ;

// Fenced code block: ```lang ... ```
codeBlock
    : CODE_FENCE lang=WORD? NEWLINE CODE_CONTENT CODE_FENCE NEWLINE?
    ;

// Unordered list
unorderedList
    : unorderedItem+
    ;

unorderedItem
    : BULLET inline+ NEWLINE?
    ;

// Ordered list
orderedList
    : orderedItem+
    ;

orderedItem
    : ORDERED_MARKER inline+ NEWLINE?
    ;

// Horizontal rule
horizontalRule
    : HR NEWLINE?
    ;

// Blockquote
blockquote
    : BLOCKQUOTE inline+ NEWLINE?
    ;

// Paragraph: one or more inline elements followed by blank line or EOF
paragraph
    : inline+ NEWLINE?
    ;

// Inline elements (nested formatting)
inline
    : boldText                                        # BoldInline
    | italicText                                      # ItalicInline
    | codeSpan                                        # CodeInline
    | link                                            # LinkInline
    | image                                           # ImageInline
    | WORD                                            # TextInline
    | SPACE                                           # SpaceInline
    | PUNCTUATION                                     # PunctInline
    ;

// **bold** or __bold__
boldText
    : DOUBLE_STAR inline+ DOUBLE_STAR
    | DOUBLE_UNDER inline+ DOUBLE_UNDER
    ;

// *italic* or _italic_
italicText
    : STAR inline+ STAR
    | UNDERSCORE inline+ UNDERSCORE
    ;

// `inline code`
codeSpan
    : BACKTICK CODE_TEXT BACKTICK
    ;

// [text](url)
link
    : LBRACKET text+=inline+ RBRACKET LPAREN url=URL RPAREN
    ;

// ![alt](url)
image
    : BANG LBRACKET alt+=inline+ RBRACKET LPAREN url=URL RPAREN
    ;

// ─── Lexer Rules ─────────────────────────────────────────────────────

// Block-level tokens
BLANK_LINE      : NEWLINE NEWLINE+ ;
HR              : ('---' '-'* | '***' '*'* | '___' '_'*) ;
CODE_FENCE      : '```' ;
BLOCKQUOTE      : '>' ' '? ;
BULLET          : [ \t]* [-*+] ' ' ;
ORDERED_MARKER  : [ \t]* [0-9]+ '.' ' ' ;

// Inline formatting markers
DOUBLE_STAR  : '**' ;
DOUBLE_UNDER : '__' ;
STAR         : '*' ;
UNDERSCORE   : '_' ;
BACKTICK     : '`' ;

// Links and images
BANG     : '!' ;
LBRACKET : '[' ;
RBRACKET : ']' ;
LPAREN   : '(' ;
RPAREN   : ')' ;

// Hash for headings
HASH : '#' ;

// URL (simplified)
URL : [a-zA-Z] [a-zA-Z0-9+\-.]* '://' ~[)\r\n ]+ ;

// Code content (between fences — everything until next fence)
CODE_CONTENT : '```' .*? '```' ;

// Inline code text (between backticks)
CODE_TEXT : ~[`\r\n]+ ;

// Words and spacing
WORD        : [a-zA-Z0-9]+ ;
SPACE       : [ \t]+ -> channel(HIDDEN) ;
NEWLINE     : '\r'? '\n' | '\r' ;
PUNCTUATION : ~[a-zA-Z0-9 \t\r\n*_`#!\\[\]()>+\-] ;
