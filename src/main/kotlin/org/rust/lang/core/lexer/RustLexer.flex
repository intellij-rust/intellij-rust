package org.rust.lang.core.lexer;

import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;

import static org.rust.lang.core.lexer.RustTokenElementTypes.*;
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

FLT_NORMAL = ({DEC_LITERAL} (\. {DEC_LITERAL} {FLT_EXP}? | {FLT_EXP}) {FLT_SUFFIX}?)
           | ({DEC_LITERAL} {FLT_SUFFIX})
FLT_TRAILING_DOT = {DEC_LITERAL} \.

FLT_EXP = [eE][+-]?{DEC_LITERAL}
FLT_SUFFIX = f32|f64

INT_LITERAL = ({DEC_LITERAL} | {HEX_LITERAL} | {OCT_LITERAL} | {BIN_LITERAL}){INT_SUFFIX}?

DEC_LITERAL = {DEC_DIGIT}({DEC_DIGIT}|_)*
HEX_LITERAL = "0x" ({HEX_DIGIT}|_)*
OCT_LITERAL = "0o" ({OCT_DIGIT}|_)*
BIN_LITERAL = "0b" ({BIN_DIGIT}|_)*

INT_SUFFIX = u8|u16|u32|u64|usize|i8|i16|i32|i64|isize

DEC_DIGIT = [0-9]
HEX_DIGIT = [a-fA-F0-9]
OCT_DIGIT = [0-7]
BIN_DIGIT = [0-1]

BYTE_LITERAL = b\' ([^'] | {ESCAPE_SEQUENCE}) \'

STRING_LITERAL = \" ([^\"\\] | {ESCAPE_SEQUENCE})* (\"|\\)?

ESCAPE_SEQUENCE = \\{EOL_WS} | {BYTE_ESCAPE} | {UNICODE_ESCAPE}
BYTE_ESCAPE     = \\n|\\r|\\t|\\\\|\\\'|\\\"|\\0|\\x{HEX_DIGIT}{2}
UNICODE_ESCAPE  = \\u\{{HEX_DIGIT}{1,6}\}


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
  "type"                          { return TYPE; }
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

  {INT_LITERAL}                   { return INTEGER_LITERAL; }

  {FLT_NORMAL}                    { return FLOAT_LITERAL; }
  {FLT_TRAILING_DOT}/[^._\p{xidstart}]
                                  { return FLOAT_LITERAL; }

  {BYTE_LITERAL}                  { return BYTE_LITERAL; }

  "b" {STRING_LITERAL} {SUFFIX}?  { return BYTE_STRING_LITERAL; }

  "br" #* \"                      { yybegin(IN_RAW_LITERAL);
                                    zzPostponedMarkedPos = zzStartRead;
                                    zzShaStride          = yylength() - 3; }

  {STRING_LITERAL} {SUFFIX}?      { return STRING_LITERAL; }

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

  <<EOF>> { return imbueBlockComment(); }

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

  \'static                              { yybegin(YYINITIAL); return STATIC_LIFETIME; }
  \'{IDENTIFIER}                        { yybegin(YYINITIAL); return LIFETIME; }
  \'\\[nrt\\\'\"0]\'         {SUFFIX}?  { yybegin(YYINITIAL); return CHAR_LITERAL; }
  \'\\x[0-9a-fA-F]{2}\'      {SUFFIX}?  { yybegin(YYINITIAL); return CHAR_LITERAL; }
  \'\\u\{[0-9a-fA-F]?{6}\}\' {SUFFIX}?  { yybegin(YYINITIAL); return CHAR_LITERAL; }
  \'.\'                      {SUFFIX}?  { yybegin(YYINITIAL); return CHAR_LITERAL; }
  \'[\x80-\xff]{2,4}\'       {SUFFIX}?  { yybegin(YYINITIAL); return CHAR_LITERAL; }
  <<EOF>>                               { yybegin(YYINITIAL); return BAD_CHARACTER; }

}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Catch All
///////////////////////////////////////////////////////////////////////////////////////////////////

[^] { return BAD_CHARACTER; }
