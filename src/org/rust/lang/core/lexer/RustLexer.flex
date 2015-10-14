package org.rust.lang.core.lexer;
import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;

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

    zzStartRead           = zzPostponedMarkedPos;
    zzPostponedMarkedPos  = -1;

    if (yylength() > 2)
    {
      if (yycharat(2) == '!')
        return RustTokenElementTypes.INNER_DOC_COMMENT;
      else if (yycharat(2) == '*')
        return RustTokenElementTypes.OUTER_DOC_COMMENT;
    }

    return RustTokenElementTypes.BLOCK_COMMENT;
  }
%}

%public
%class _RustLexer
%implements FlexLexer
%function advance
%type IElementType

%s BLOCK_COMMENT
%s EOL_COMMENT

%s LIFETIME_OR_CHAR

%s RAW_LITERAL

%s SUFFIX

%unicode

///////////////////////////////////////////////////////////////////////////////////////////////////
// Whitespaces
///////////////////////////////////////////////////////////////////////////////////////////////////

EOL_WS  = \r|\n|\r\n
LINE_WS = [\ \t]

WHITE_SPACE = ({LINE_WS}|{EOL_WS})+

///////////////////////////////////////////////////////////////////////////////////////////////////
// Identifier
///////////////////////////////////////////////////////////////////////////////////////////////////

IDENTIFIER=[_\p{xidstart}][\p{xidcontinue}]*

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
HEX_LITERAL = 0x({HEX_DIGIT}|_)*
OCT_LITERAL = 0o({OCT_DIGIT}|_)*
BIN_LITERAL = 0b({BIN_DIGIT}|_)*

INT_SUFFIX = u8|u16|u32|u64|usize|i8|i16|i32|i64|isize

DEC_DIGIT = [0-9]
HEX_DIGIT = [A-F0-9]
OCT_DIGIT = [0-7]
BIN_DIGIT = [0-1]

BYTE_LITERAL = b\x27 ([^'] | {ESCAPE_SEQUENCE}) \x27

STRING_LITERAL = r? \x22 ([^\"\\] | {ESCAPE_SEQUENCE})* (\x22|\\)?

ESCAPE_SEQUENCE = \\[^\r\n\t\\] | \\\R | {BYTE_ESCAPE} | {UNICODE_ESCAPE}
BYTE_ESCAPE = \\n|\\r|\\t|\\\\|\\x{HEX_DIGIT}{2}
UNICODE_ESCAPE = \\u\{{HEX_DIGIT}{1,6}\}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Other
///////////////////////////////////////////////////////////////////////////////////////////////////

SHEBANG_LINE=\#\![^\[].*

%%
<YYINITIAL> \x27                  { yybegin(LIFETIME_OR_CHAR); yypushback(1); }

<YYINITIAL>                       {

  "{"                             { return RustTokenElementTypes.LBRACE; }
  "}"                             { return RustTokenElementTypes.RBRACE; }
  "["                             { return RustTokenElementTypes.LBRACK; }
  "]"                             { return RustTokenElementTypes.RBRACK; }
  "("                             { return RustTokenElementTypes.LPAREN; }
  ")"                             { return RustTokenElementTypes.RPAREN; }
  "::"                            { return RustTokenElementTypes.COLONCOLON; }
  ":"                             { return RustTokenElementTypes.COLON; }
  ";"                             { return RustTokenElementTypes.SEMICOLON; }
  ","                             { return RustTokenElementTypes.COMMA; }
  "."                             { return RustTokenElementTypes.DOT; }
  ".."                            { return RustTokenElementTypes.DOTDOT; }
  "..."                           { return RustTokenElementTypes.DOTDOTDOT; }
  "="                             { return RustTokenElementTypes.EQ; }
  "!="                            { return RustTokenElementTypes.EXCLEQ; }
  "=="                            { return RustTokenElementTypes.EQEQ; }
  "!"                             { return RustTokenElementTypes.EXCL; }
  "++"                            { return RustTokenElementTypes.PLUSPLUS; }
  "+="                            { return RustTokenElementTypes.PLUSEQ; }
  "+"                             { return RustTokenElementTypes.PLUS; }
  "--"                            { return RustTokenElementTypes.MINUSMINUS; }
  "-="                            { return RustTokenElementTypes.MINUSEQ; }
  "-"                             { return RustTokenElementTypes.MINUS; }
  "#"                             { return RustTokenElementTypes.SHA; }
  "#!"                            { return RustTokenElementTypes.SHEBANG; }
  "||"                            { return RustTokenElementTypes.OROR; }
  "|="                            { return RustTokenElementTypes.OREQ; }
  "&&"                            { return RustTokenElementTypes.ANDAND; }
  "&="                            { return RustTokenElementTypes.ANDEQ; }
  "&"                             { return RustTokenElementTypes.AND; }
  "|"                             { return RustTokenElementTypes.OR; }
  "<"                             { return RustTokenElementTypes.LT; }
  "^="                            { return RustTokenElementTypes.XOREQ; }
  "^"                             { return RustTokenElementTypes.XOR; }
  "*="                            { return RustTokenElementTypes.MULEQ; }
  "*"                             { return RustTokenElementTypes.MUL; }
  "/="                            { return RustTokenElementTypes.DIVEQ; }
  "/"                             { return RustTokenElementTypes.DIV; }
  "%="                            { return RustTokenElementTypes.REMEQ; }
  "%"                             { return RustTokenElementTypes.REM; }
  ">"                             { return RustTokenElementTypes.GT; }
  "->"                            { return RustTokenElementTypes.ARROW; }
  "=>"                            { return RustTokenElementTypes.FAT_ARROW; }
  "?"                             { return RustTokenElementTypes.Q; }
  "@"                             { return RustTokenElementTypes.AT; }
  "_"                             { return RustTokenElementTypes.UNDERSCORE; }
  "$"                             { return RustTokenElementTypes.DOLLAR; }

  "abstract"                      { return RustTokenElementTypes.ABSTRACT; }
  "alignof"                       { return RustTokenElementTypes.ALIGNOF; }
  "as"                            { return RustTokenElementTypes.AS; }
  "become"                        { return RustTokenElementTypes.BECOME; }
  "box"                           { return RustTokenElementTypes.BOX; }
  "break"                         { return RustTokenElementTypes.BREAK; }
  "const"                         { return RustTokenElementTypes.CONST; }
  "continue"                      { return RustTokenElementTypes.CONTINUE; }
  "crate"                         { return RustTokenElementTypes.CRATE; }
  "do"                            { return RustTokenElementTypes.DO; }
  "else"                          { return RustTokenElementTypes.ELSE; }
  "enum"                          { return RustTokenElementTypes.ENUM; }
  "extern"                        { return RustTokenElementTypes.EXTERN; }
  "false"                         { return RustTokenElementTypes.FALSE; }
  "final"                         { return RustTokenElementTypes.FINAL; }
  "fn"                            { return RustTokenElementTypes.FN; }
  "for"                           { return RustTokenElementTypes.FOR; }
  "if"                            { return RustTokenElementTypes.IF; }
  "impl"                          { return RustTokenElementTypes.IMPL; }
  "in"                            { return RustTokenElementTypes.IN; }
  "let"                           { return RustTokenElementTypes.LET; }
  "loop"                          { return RustTokenElementTypes.LOOP; }
  "macro"                         { return RustTokenElementTypes.MACRO; }
  "match"                         { return RustTokenElementTypes.MATCH; }
  "mod"                           { return RustTokenElementTypes.MOD; }
  "move"                          { return RustTokenElementTypes.MOVE; }
  "mut"                           { return RustTokenElementTypes.MUT; }
  "offsetof"                      { return RustTokenElementTypes.OFFSETOF; }
  "override"                      { return RustTokenElementTypes.OVERRIDE; }
  "priv"                          { return RustTokenElementTypes.PRIV; }
  "proc"                          { return RustTokenElementTypes.PROC; }
  "pub"                           { return RustTokenElementTypes.PUB; }
  "pure"                          { return RustTokenElementTypes.PURE; }
  "ref"                           { return RustTokenElementTypes.REF; }
  "return"                        { return RustTokenElementTypes.RETURN; }
  "Self"                          { return RustTokenElementTypes.CSELF; }
  "self"                          { return RustTokenElementTypes.SELF; }
  "sizeof"                        { return RustTokenElementTypes.SIZEOF; }
  "static"                        { return RustTokenElementTypes.STATIC; }
  "struct"                        { return RustTokenElementTypes.STRUCT; }
  "super"                         { return RustTokenElementTypes.SUPER; }
  "trait"                         { return RustTokenElementTypes.TRAIT; }
  "true"                          { return RustTokenElementTypes.TRUE; }
  "type"                          { return RustTokenElementTypes.TYPE; }
  "typeof"                        { return RustTokenElementTypes.TYPEOF; }
  "unsafe"                        { return RustTokenElementTypes.UNSAFE; }
  "unsized"                       { return RustTokenElementTypes.UNSIZED; }
  "use"                           { return RustTokenElementTypes.USE; }
  "virtual"                       { return RustTokenElementTypes.VIRTUAL; }
  "where"                         { return RustTokenElementTypes.WHERE; }
  "while"                         { return RustTokenElementTypes.WHILE; }
  "yield"                         { return RustTokenElementTypes.YIELD; }

  "/*"                            { yybegin(BLOCK_COMMENT); yypushback(2); }
  "//"                            { yybegin(EOL_COMMENT);   yypushback(2); }

  {IDENTIFIER}                    { return RustTokenElementTypes.IDENTIFIER; }

  /* LITERALS */

  {INT_LITERAL}                   { return RustTokenElementTypes.INTEGER_LITERAL; }

  {FLT_NORMAL}                    { return RustTokenElementTypes.FLOAT_LITERAL; }
  {FLT_TRAILING_DOT}/[^._\p{xidstart}]
                                  { return RustTokenElementTypes.FLOAT_LITERAL; }

  {BYTE_LITERAL}                  { return RustTokenElementTypes.BYTE_LITERAL; }

  "b"{STRING_LITERAL}             { yybegin(SUFFIX); return RustTokenElementTypes.BYTE_STRING_LITERAL; }
  "br"{STRING_LITERAL}            { yybegin(SUFFIX); return RustTokenElementTypes.RAW_BYTE_STRING_LITERAL; }

  "br" #+ \x22                    { yybegin(RAW_LITERAL);

                                    zzPostponedMarkedPos = zzStartRead;
                                    zzShaStride          = yylength() - 3; }

  {STRING_LITERAL}                { yybegin(SUFFIX); return RustTokenElementTypes.STRING_LITERAL; }
  "r"{STRING_LITERAL}             { yybegin(SUFFIX); return RustTokenElementTypes.RAW_STRING_LITERAL; }

  "r" #+ \x22                     { yybegin(RAW_LITERAL);

                                    zzPostponedMarkedPos = zzStartRead;
                                    zzShaStride          = yylength() - 2; }

  {SHEBANG_LINE}                  { return RustTokenElementTypes.SHEBANG_LINE; }

  {WHITE_SPACE}                   { return com.intellij.psi.TokenType.WHITE_SPACE; }
}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Suffix
///////////////////////////////////////////////////////////////////////////////////////////////////

<SUFFIX>{IDENTIFIER}    { yybegin(YYINITIAL); }
<SUFFIX>[^]             { yypushback(1); yybegin(YYINITIAL); }

///////////////////////////////////////////////////////////////////////////////////////////////////
// Literals
///////////////////////////////////////////////////////////////////////////////////////////////////

<RAW_LITERAL> {

  \x22 #+ {
    int shaExcess = yylength() - 1 - zzShaStride;
    if (shaExcess >= 0) {
      yybegin(SUFFIX);
      yypushback(shaExcess);

      zzStartRead = zzPostponedMarkedPos;

      zzShaStride           = -1;
      zzPostponedMarkedPos  = -1;

      if (yycharat(0) == 'b')
        return RustTokenElementTypes.RAW_BYTE_STRING_LITERAL;
      else
        return RustTokenElementTypes.RAW_STRING_LITERAL;
    }
  }

  [^]       { }
  <<EOF>>   { zzShaStride          = -1;
              zzPostponedMarkedPos = -1; }

}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Comments
///////////////////////////////////////////////////////////////////////////////////////////////////

<BLOCK_COMMENT> {
  "/*"    { if (zzNestedCommentLevel++ == 0)
              zzPostponedMarkedPos = zzStartRead;
          }

  "*/"    { if (--zzNestedCommentLevel == 0)
              return imbueBlockComment();
          }

  <<EOF>> { return imbueBlockComment(); }

  [^]     { }
}

<EOL_COMMENT>.*
{
    yybegin(YYINITIAL);

    if (yycharat(2) == '!')
        return RustTokenElementTypes.INNER_DOC_COMMENT;
    else if (yycharat(2) == '/')
        return RustTokenElementTypes.OUTER_DOC_COMMENT;
    else
        return RustTokenElementTypes.EOL_COMMENT;
}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Lifetimes & Literals
///////////////////////////////////////////////////////////////////////////////////////////////////

<LIFETIME_OR_CHAR> {

  \x27static                          { yybegin(YYINITIAL); return RustTokenElementTypes.STATIC_LIFETIME; }
  \x27{IDENTIFIER}                    { yybegin(YYINITIAL); return RustTokenElementTypes.LIFETIME; }
  \x27\\[nrt\\\x27\x220]\x27          { yybegin(SUFFIX);    return RustTokenElementTypes.CHAR_LITERAL; }
  \x27\\x[0-9a-fA-F]{2}\x27           { yybegin(SUFFIX);    return RustTokenElementTypes.CHAR_LITERAL; }
  \x27\\u\{[0-9a-fA-F]?{6}\}\x27      { yybegin(SUFFIX);    return RustTokenElementTypes.CHAR_LITERAL; }
  \x27.\x27                           { yybegin(SUFFIX);    return RustTokenElementTypes.CHAR_LITERAL; }
  \x27[\x80-\xff]{2,4}\x27            { yybegin(SUFFIX);    return RustTokenElementTypes.CHAR_LITERAL; }
  <<EOF>>                             { yybegin(YYINITIAL); return com.intellij.psi.TokenType.BAD_CHARACTER; }

}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Catch All
///////////////////////////////////////////////////////////////////////////////////////////////////

[^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
