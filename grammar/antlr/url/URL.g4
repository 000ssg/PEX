/**
 * URL/URI Grammar (ANTLR4)
 * Based on RFC 3986 — Uniform Resource Identifier (URI): Generic Syntax
 *
 * Demonstrates: fragment rules for character classes, complex lexer patterns,
 * character class negation ~, reusable fragments composing larger patterns.
 */
grammar URL;

// ─── Parser Rules ────────────────────────────────────────────────────

// Full URI: scheme://authority/path?query#fragment
uri
    : scheme COLON SLASH SLASH authority path? (QUESTION query)? (HASH fragment_)? EOF
    ;

scheme
    : SCHEME
    ;

authority
    : (userinfo AT)? host (COLON port)?
    ;

userinfo
    : USERINFO
    ;

host
    : IP_V4                                           # IPv4Host
    | LBRACKET IP_V6 RBRACKET                         # IPv6Host
    | HOSTNAME                                        # NamedHost
    ;

port
    : DIGITS
    ;

path
    : (SLASH SEGMENT)*
    ;

query
    : queryParam (AMP queryParam)*
    ;

queryParam
    : key=QCHAR+ (EQUALS value=QCHAR+)?
    ;

fragment_
    : FCHAR*
    ;

// ─── Lexer Rules ─────────────────────────────────────────────────────

// Scheme: http, https, ftp, etc.
SCHEME : [a-zA-Z] [a-zA-Z0-9+\-.]* ;

// User info: user:password (before @)
USERINFO : [a-zA-Z0-9_.~!$&'()*+,;=:%\-]+ ;

// IPv4 address: 192.168.1.1
IP_V4 : OCTET '.' OCTET '.' OCTET '.' OCTET ;

// IPv6 address (simplified)
IP_V6 : [0-9a-fA-F:]+ ;

// Hostname: www.example.com
HOSTNAME : [a-zA-Z0-9] ([a-zA-Z0-9\-]* [a-zA-Z0-9])? ('.' [a-zA-Z0-9] ([a-zA-Z0-9\-]* [a-zA-Z0-9])?)* ;

// Path segment
SEGMENT : [a-zA-Z0-9_.~!$&'()*+,;=:@%\-]+ ;

// Digits (for port numbers)
DIGITS : [0-9]+ ;

// Query characters
QCHAR : [a-zA-Z0-9_.~!$'()*+,;:@/%\-] ;

// Fragment characters
FCHAR : [a-zA-Z0-9_.~!$&'()*+,;=:@/?%\-] ;

// Punctuation
COLON    : ':' ;
SLASH    : '/' ;
QUESTION : '?' ;
HASH     : '#' ;
AT       : '@' ;
AMP      : '&' ;
EQUALS   : '=' ;
LBRACKET : '[' ;
RBRACKET : ']' ;

// ─── Fragment Rules ──────────────────────────────────────────────────

// IPv4 octet: 0-255
fragment OCTET
    : '25' [0-5]                                      // 250-255
    | '2' [0-4] [0-9]                                 // 200-249
    | [01]? [0-9] [0-9]?                              // 0-199
    ;

// Percent-encoded character: %XX
fragment PCT_ENCODED
    : '%' [0-9a-fA-F] [0-9a-fA-F]
    ;

// Unreserved characters per RFC 3986
fragment UNRESERVED
    : [a-zA-Z0-9\-._~]
    ;

// Sub-delimiter characters per RFC 3986
fragment SUB_DELIMS
    : [!$&'()*+,;=]
    ;
