package org.rust.lang.core.lexer;

import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;

import static org.rust.lang.core.psi.RustTokenElementTypes.*;
import static com.intellij.psi.TokenType.*;

%%

%{
  public _RustLexer() {
    this((java.io.Reader)null);
  }
%}

%{}
  /**
    * '#+' stride demarking start/end of raw string/byte literal
    */
  private int zzShaStride = -1;

  /**
    * Dedicated storage for starting position of some previously successful
    * match
    */
  private int zzPostponedMarkedPos = -1;

  /**
    * Dedicated nested-comment level counter
    */
  private int zzNestedCommentLevel = 0;
%}

%{
  IElementType imbueBlockComment() {
      assert(zzNestedCommentLevel == 0);
      yybegin(YYINITIAL);

      zzStartRead = zzPostponedMarkedPos;
      zzPostponedMarkedPos = -1;

      if (yylength() >= 3) {
          if (yycharat(2) == '!') {
              return INNER_DOC_COMMENT;
          } else if (yycharat(2) == '*' && (yylength() == 3 || yycharat(3) != '*' && yycharat(3) != '/')) {
              return OUTER_DOC_COMMENT;
          }
      }

      return BLOCK_COMMENT;
  }

  IElementType imbueRawLiteral() {
      yybegin(YYINITIAL);

      zzStartRead = zzPostponedMarkedPos;
      zzShaStride = -1;
      zzPostponedMarkedPos = -1;

      return yycharat(0) == 'b' ? RAW_BYTE_STRING_LITERAL : RAW_STRING_LITERAL;
  }
%}

%public
%class _RustLexer
%implements FlexLexer
%function advance
%type IElementType

%s IN_BLOCK_COMMENT
%s IN_EOL_COMMENT

%s IN_LIFETIME_OR_CHAR

%s IN_RAW_LITERAL
%s IN_RAW_LITERAL_SUFFIX

%unicode

///////////////////////////////////////////////////////////////////////////////////////////////////
// Whitespaces
///////////////////////////////////////////////////////////////////////////////////////////////////

EOL_WS           = \n | \r | \r\n
LINE_WS          = [\ \t]
WHITE_SPACE_CHAR = {EOL_WS} | {LINE_WS}
WHITE_SPACE      = {WHITE_SPACE_CHAR}+

///////////////////////////////////////////////////////////////////////////////////////////////////
// Identifier
///////////////////////////////////////////////////////////////////////////////////////////////////

IDENTIFIER = [_\p{xidstart}][\p{xidcontinue}]*
SUFFIX     = {IDENTIFIER}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Literals
///////////////////////////////////////////////////////////////////////////////////////////////////

EXPONENT      = [eE] [-+]? [0-9_]+
FLT_SUFFIX    = f32 | f64
FLT_LITERAL   = ( {DEC_LITERAL} \. {DEC_LITERAL} {EXPONENT}? {SUFFIX}? )
              | ( {DEC_LITERAL} {EXPONENT} {SUFFIX}? )
FLT_TDOT      = {DEC_LITERAL} \.


INT_LITERAL = ( {DEC_LITERAL}
              | {HEX_LITERAL}
              | {OCT_LITERAL}
              | {BIN_LITERAL} ) {SUFFIX}?

DEC_LITERAL = [0-9] [0-9_]*
HEX_LITERAL = "0x" [a-fA-F0-9_]*
OCT_LITERAL = "0o" [0-7_]*
BIN_LITERAL = "0b" [01_]*


CHAR_LITERAL   = ( \' ( [^\\\'\r\n] | \\[^\r\n] | "\\x" [a-fA-F0-9]+ | "\\u{" [a-fA-F0-9]* "}"? )? ( \' {SUFFIX}? | \\ )? )
               | ( \' [\p{xidcontinue}]* \' {SUFFIX}? )
STRING_LITERAL = \" ( [^\\\"] | \\[^] )* ( \" {SUFFIX}? | \\ )?


///////////////////////////////////////////////////////////////////////////////////////////////////
// Other
///////////////////////////////////////////////////////////////////////////////////////////////////

SHEBANG_LINE=\#\![^\[].*

%%

<YYINITIAL> \'                    { yybegin(IN_LIFETIME_OR_CHAR); yypushback(1); }

<YYINITIAL>                       {

  "{"                             { return LBRACE; }
  "}"                             { return RBRACE; }
  "["                             { return LBRACK; }
  "]"                             { return RBRACK; }
  "("                             { return LPAREN; }
  ")"                             { return RPAREN; }
  "::"                            { return COLONCOLON; }
  ":"                             { return COLON; }
  ";"                             { return SEMICOLON; }
  ","                             { return COMMA; }
  "."                             { return DOT; }
  ".."                            { return DOTDOT; }
  "..."                           { return DOTDOTDOT; }
  "="                             { return EQ; }
  "!="                            { return EXCLEQ; }
  "=="                            { return EQEQ; }
  "!"                             { return EXCL; }
  "+="                            { return PLUSEQ; }
  "+"                             { return PLUS; }
  "-="                            { return MINUSEQ; }
  "-"                             { return MINUS; }
  "#"                             { return SHA; }
  "#!"                            { return SHEBANG; }
  "||"                            { return OROR; }
  "|="                            { return OREQ; }
  "&&"                            { return ANDAND; }
  "&="                            { return ANDEQ; }
  "&"                             { return AND; }
  "|"                             { return OR; }
  "<"                             { return LT; }
  "^="                            { return XOREQ; }
  "^"                             { return XOR; }
  "*="                            { return MULEQ; }
  "*"                             { return MUL; }
  "/="                            { return DIVEQ; }
  "/"                             { return DIV; }
  "%="                            { return REMEQ; }
  "%"                             { return REM; }
  ">"                             { return GT; }
  "->"                            { return ARROW; }
  "=>"                            { return FAT_ARROW; }
  "?"                             { return Q; }
  "@"                             { return AT; }
  "_"                             { return UNDERSCORE; }
  "$"                             { return DOLLAR; }

  "abstract"                      { return ABSTRACT; }
  "alignof"                       { return ALIGNOF; }
  "as"                            { return AS; }
  "become"                        { return BECOME; }
  "box"                           { return BOX; }
  "break"                         { return BREAK; }
  "const"                         { return CONST; }
  "continue"                      { return CONTINUE; }
  "crate"                         { return CRATE; }
  "do"                            { return DO; }
  "else"                          { return ELSE; }
  "enum"                          { return ENUM; }
  "extern"                        { return EXTERN; }
  "false"                         { return FALSE; }
  "final"                         { return FINAL; }
  "fn"                            { return FN; }
  "for"                           { return FOR; }
  "if"                            { return IF; }
  "impl"                          { return IMPL; }
  "in"                            { return IN; }
  "let"                           { return LET; }
  "loop"                          { return LOOP; }
  "macro"                         { return MACRO; }
  "match"                         { return MATCH; }
  "mod"                           { return MOD; }
  "move"                          { return MOVE; }
  "mut"                           { return MUT; }
  "offsetof"                      { return OFFSETOF; }
  "override"                      { return OVERRIDE; }
  "priv"                          { return PRIV; }
  "proc"                          { return PROC; }
  "pub"                           { return PUB; }
  "pure"                          { return PURE; }
  "ref"                           { return REF; }
  "return"                        { return RETURN; }
  "Self"                          { return CSELF; }
  "self"                          { return SELF; }
  "sizeof"                        { return SIZEOF; }
  "static"                        { return STATIC; }
  "struct"                        { return STRUCT; }
  "super"                         { return SUPER; }
  "trait"                         { return TRAIT; }
  "true"                          { return TRUE; }
  "type"                          { return TYPE_KW; }
  "typeof"                        { return TYPEOF; }
  "unsafe"                        { return UNSAFE; }
  "unsized"                       { return UNSIZED; }
  "use"                           { return USE; }
  "virtual"                       { return VIRTUAL; }
  "where"                         { return WHERE; }
  "while"                         { return WHILE; }
  "yield"                         { return YIELD; }

  "/*"                            { yybegin(IN_BLOCK_COMMENT); yypushback(2); }
  "//"                            { yybegin(IN_EOL_COMMENT);   yypushback(2); }

  {IDENTIFIER}                    { return IDENTIFIER; }

  /* LITERALS */

  // Match 1f32 and 1f64 as floats, not integers (kinda hack)
  {DEC_LITERAL} {FLT_SUFFIX}      { return FLOAT_LITERAL; }

  {INT_LITERAL}                   { return INTEGER_LITERAL; }

  {FLT_LITERAL}                   { return FLOAT_LITERAL; }

  // Correctly handle 1.f32 and 0..9
  {FLT_TDOT} / [^.\p{xidstart}]   { return FLOAT_LITERAL; }

  "b" {CHAR_LITERAL}              { return BYTE_LITERAL; }

  "b" {STRING_LITERAL}            { return BYTE_STRING_LITERAL; }
  {STRING_LITERAL}                { return STRING_LITERAL; }

  "br" #* \"                      { yybegin(IN_RAW_LITERAL);
                                    zzPostponedMarkedPos = zzStartRead;
                                    zzShaStride          = yylength() - 3; }

  "r" #* \"                       { yybegin(IN_RAW_LITERAL);
                                    zzPostponedMarkedPos = zzStartRead;
                                    zzShaStride          = yylength() - 2; }

  {SHEBANG_LINE}                  { return SHEBANG_LINE; }

  {WHITE_SPACE}                   { return WHITE_SPACE; }
}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Literals
///////////////////////////////////////////////////////////////////////////////////////////////////

<IN_RAW_LITERAL> {

  \" #* {
    int shaExcess = yylength() - 1 - zzShaStride;
    if (shaExcess >= 0) {
      yybegin(IN_RAW_LITERAL_SUFFIX);
      yypushback(shaExcess);
    }
  }

  [^]       { }
  <<EOF>>   {
    return imbueRawLiteral();
  }

}

<IN_RAW_LITERAL_SUFFIX> {
  {SUFFIX}  { return imbueRawLiteral(); }
  [^]       { yypushback(1); return imbueRawLiteral(); }
  <<EOF>>   { return imbueRawLiteral(); }
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Comments
///////////////////////////////////////////////////////////////////////////////////////////////////

<IN_BLOCK_COMMENT> {
  "/*"    { if (zzNestedCommentLevel++ == 0)
              zzPostponedMarkedPos = zzStartRead;
          }

  "*/"    { if (--zzNestedCommentLevel == 0)
              return imbueBlockComment();
          }

  <<EOF>> { zzNestedCommentLevel = 0; return imbueBlockComment(); }

  [^]     { }
}

<IN_EOL_COMMENT>.* {
    yybegin(YYINITIAL);

    if (yylength() >= 3) {
        if (yycharat(2) == '!') {
            return INNER_DOC_COMMENT;
        } else if (yycharat(2) == '/' && (yylength() == 3 || yycharat(3) != '/')) {
            return OUTER_DOC_COMMENT;
        }
    }
    return EOL_COMMENT;
}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Lifetimes & Literals
///////////////////////////////////////////////////////////////////////////////////////////////////

<IN_LIFETIME_OR_CHAR> {
  \'{IDENTIFIER}                        { yybegin(YYINITIAL); return LIFETIME; }
  {CHAR_LITERAL}                        { yybegin(YYINITIAL); return CHAR_LITERAL; }
  <<EOF>>                               { yybegin(YYINITIAL); return BAD_CHARACTER; }
}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Catch All
///////////////////////////////////////////////////////////////////////////////////////////////////

[^] { return BAD_CHARACTER; }
