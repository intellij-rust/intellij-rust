package org.rust.lang.core.lexer;

import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;

import static org.rust.lang.core.psi.RsElementTypes.*;
import static org.rust.lang.core.parser.RustParserDefinition.*;
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
      yybegin(INITIAL);

      zzStartRead = zzPostponedMarkedPos;
      zzPostponedMarkedPos = -1;

      if (yylength() >= 3) {
          if (yycharat(2) == '!') {
              return INNER_BLOCK_DOC_COMMENT;
          } else if (yycharat(2) == '*' && (yylength() == 3 || yycharat(3) != '*' && yycharat(3) != '/')) {
              return OUTER_BLOCK_DOC_COMMENT;
          }
      }

      return BLOCK_COMMENT;
  }

  IElementType imbueRawLiteral() {
      yybegin(INITIAL);

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

%s INITIAL

%s IN_BLOCK_COMMENT

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

IDENTIFIER = ("r#")?[_\p{xidstart}][\p{xidcontinue}]*
SUFFIX     = {IDENTIFIER}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Literals
///////////////////////////////////////////////////////////////////////////////////////////////////

EXPONENT      = [eE] [-+]? [0-9_]+

FLT_LITERAL   = ( {DEC_LITERAL} \. {DEC_LITERAL} {EXPONENT}? {SUFFIX}? )
              | ( {DEC_LITERAL} {EXPONENT} {SUFFIX}? )
              | ( {DEC_LITERAL} "f" [\p{xidcontinue}]* )

FLT_LITERAL_TDOT = {DEC_LITERAL} \.

INT_LITERAL = ( {DEC_LITERAL}
              | {HEX_LITERAL}
              | {OCT_LITERAL}
              | {BIN_LITERAL} ) {SUFFIX}?

DEC_LITERAL = [0-9] [0-9_]*
HEX_LITERAL = "0x" [a-fA-F0-9_]*
OCT_LITERAL = "0o" [0-7_]*
BIN_LITERAL = "0b" [01_]*


CHAR_LITERAL   = ( \' ( [^\\\'\r\n] | \\[^\r\n] | "\\x" [a-fA-F0-9]+ | "\\u{" [a-fA-F0-9][a-fA-F0-9_]* "}"? )? ( \' {SUFFIX}? | \\ )? )
               | ( \' [\p{xidcontinue}]* \' {SUFFIX}? )
STRING_LITERAL = \" ( [^\\\"] | \\[^] )* ( \" {SUFFIX}? | \\ )?

INNER_EOL_DOC = ({LINE_WS}*"//!".*{EOL_WS})*({LINE_WS}*"//!".*)
// !(!a|b) is a (set) difference between a and b.
EOL_DOC_LINE  = {LINE_WS}*!(!("///".*)|("////".*))
OUTER_EOL_DOC = ({EOL_DOC_LINE}{EOL_WS})*{EOL_DOC_LINE}

%%

<YYINITIAL> {
  "#!" [\r\n]                     { yybegin(INITIAL); yypushback(1); return SHEBANG_LINE; }
  "#!" [^\[\r\n] [^\r\n]*         { yybegin(INITIAL); return SHEBANG_LINE; }
  [^]                             { yybegin(INITIAL); yypushback(1); }
}

<INITIAL> \'                    { yybegin(IN_LIFETIME_OR_CHAR); yypushback(1); }

<INITIAL>                       {

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
  "..="                           { return DOTDOTEQ; }
  "="                             { return EQ; }
  "!="                            { return EXCLEQ; }
  "=="                            { return EQEQ; }
  "!"                             { return EXCL; }
  "+="                            { return PLUSEQ; }
  "+"                             { return PLUS; }
  "-="                            { return MINUSEQ; }
  "-"                             { return MINUS; }
  "#"                             { return SHA; }
  "|="                            { return OREQ; }
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

  "abstract"|"become"|"do"|"final"|"override"|"priv"|"typeof"|"unsized"|"virtual"
                                  { return RESERVED_KEYWORD; }

  "true"|"false"                  { return BOOL_LITERAL; }
  "as"                            { return AS; }
  "box"                           { return BOX; }
  "break"                         { return BREAK; }
  "const"                         { return CONST; }
  "continue"                      { return CONTINUE; }
  "crate"                         { return CRATE; }
  "else"                          { return ELSE; }
  "enum"                          { return ENUM; }
  "extern"                        { return EXTERN; }
  "fn"                            { return FN; }
  "for"                           { return FOR; }
  "if"                            { return IF; }
  "impl"                          { return IMPL; }
  "in"                            { return IN; }
  "let"                           { return LET; }
  "loop"                          { return LOOP; }
  "macro"                         { return MACRO_KW; }
  "match"                         { return MATCH; }
  "mod"                           { return MOD; }
  "move"                          { return MOVE; }
  "mut"                           { return MUT; }
  "pub"                           { return PUB; }
  "ref"                           { return REF; }
  "return"                        { return RETURN; }
  "Self"                          { return CSELF; }
  "self"                          { return SELF; }
  "static"                        { return STATIC; }
  "struct"                        { return STRUCT; }
  "super"                         { return SUPER; }
  "trait"                         { return TRAIT; }
  "type"                          { return TYPE_KW; }
  "unsafe"                        { return UNSAFE; }
  "use"                           { return USE; }
  "where"                         { return WHERE; }
  "while"                         { return WHILE; }
  "yield"                         { return YIELD; }

  "/*"                            { yybegin(IN_BLOCK_COMMENT); yypushback(2); }

  {INNER_EOL_DOC}                 { return INNER_EOL_DOC_COMMENT; }
  {OUTER_EOL_DOC}                 { return OUTER_EOL_DOC_COMMENT; }
  "//" .*                         { return EOL_COMMENT; }

  {IDENTIFIER}                    { return IDENTIFIER; }

  /* LITERALS */

  // Floats must come first, to parse 1e1 as a float and not as an integer with a suffix
  {FLT_LITERAL}                   { return FLOAT_LITERAL; }

  {INT_LITERAL}                   { return INTEGER_LITERAL; }

  // Correctly handle 1.f32 and 0..9
  {FLT_LITERAL_TDOT} / [^.\p{xidstart}] { return FLOAT_LITERAL; }

  "b" {CHAR_LITERAL}              { return BYTE_LITERAL; }

  "b" {STRING_LITERAL}            { return BYTE_STRING_LITERAL; }
  {STRING_LITERAL}                { return STRING_LITERAL; }

  "br" #* \"                      { yybegin(IN_RAW_LITERAL);
                                    zzPostponedMarkedPos = zzStartRead;
                                    zzShaStride          = yylength() - 3; }

  "r" #* \"                       { yybegin(IN_RAW_LITERAL);
                                    zzPostponedMarkedPos = zzStartRead;
                                    zzShaStride          = yylength() - 2; }

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

///////////////////////////////////////////////////////////////////////////////////////////////////
// Quote identifiers & Literals
///////////////////////////////////////////////////////////////////////////////////////////////////

<IN_LIFETIME_OR_CHAR> {
  \'{IDENTIFIER}                        { yybegin(INITIAL); return QUOTE_IDENTIFIER; }
  {CHAR_LITERAL}                        { yybegin(INITIAL); return CHAR_LITERAL; }
  <<EOF>>                               { yybegin(INITIAL); return BAD_CHARACTER; }
}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Catch All
///////////////////////////////////////////////////////////////////////////////////////////////////

[^] { return BAD_CHARACTER; }
