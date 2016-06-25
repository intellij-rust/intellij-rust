package org.rust.lang.core.lexer;

import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;

import static org.rust.lang.core.psi.RustDocElementTypes.*;
import static com.intellij.psi.TokenType.*;

%%

%{
  public _RustDocLexer() {
    this((java.io.Reader)null);
  }

  private int MAIN_STATE = YYINITIAL;
  private int DATA_STATE = IN_DOC_DATA;

  private boolean isLastToken() {
    return zzMarkedPos == zzEndRead;
  }

  private void docHeadingTrimRight() {
    int i = yylength() - 1;
    char ch = yycharat(i);

    // Trim trailing *****/ if we are at the end of block doc comment
    if (i >= 1 && MAIN_STATE == IN_BLOCK && isLastToken() && ch == '/' && yycharat(i - 1) == '*') {
      i -= 2;
      ch = yycharat(i);
      while (ch == '*') {
        ch = yycharat(--i);
      }
    }

    // Trim trailing whitespace
    while (ch == ' ' || ch == '\t') {
      ch = yycharat(--i);
    }

    yypushback(yylength() - i - 1);
  }
%}

%public
%class _RustDocLexer
%implements FlexLexer
%function advance
%type IElementType

%s IN_BLOCK
%s IN_EOL
%s IN_DOC_DATA
%s IN_DOC_DATA_DEEP

%unicode

///////////////////////////////////////////////////////////////////////////////////////////////////
// Whitespaces
///////////////////////////////////////////////////////////////////////////////////////////////////

EOL_WS           = \n | \r | \r\n
LINE_WS          = [\ \t]
WHITE_SPACE_CHAR = {EOL_WS} | {LINE_WS}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Markdown/CommonMark macros
///////////////////////////////////////////////////////////////////////////////////////////////////

HEADING_HASH = "#"{1,6}

// http://spec.commonmark.org/0.25/#links
LINK_TEXT   = "[" ( [^\]\r\n] | "\\]" )* "]"
LINK_DEST   = ( "<" ( [^>\ \t\r\n] | "\\>" )* ">" )
            | ( [^\(\)\ \t\r\n] | "\\(" | "\\)" )*
LINK_TITLE  = ( \" ( [^\"\r\n] | \\\" )* \" )
            | ( \' ( [^\'\r\n] | \\\' )* \' )
            | ( "(" ( [^\)\r\n] | "\\)" )* ")" )
LINK_LABEL  = "[" ( [^\]\ \t\r\n] | "\\]" ) ( [^\]\r\n] | "\\]" )* "]"

INLINE_LINK     = {LINK_TEXT} "(" {LINE_WS}* ( {LINK_DEST} ( {LINE_WS}+ {LINK_TITLE} )? )? {LINE_WS}* ")"
REF_LINK        = ( {LINK_TEXT} {LINK_LABEL} )
                | ( {LINK_LABEL} "[]"? )
LINK_REF_DEF    = {LINK_LABEL} ":" [^\r\n]*

%%

<YYINITIAL> {
    "/*"    { MAIN_STATE = IN_BLOCK; yybegin(IN_BLOCK); yypushback(2); }
    "//"    { MAIN_STATE = IN_EOL;   yybegin(IN_EOL);   yypushback(2); }
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Block docs
///////////////////////////////////////////////////////////////////////////////////////////////////

<IN_BLOCK> {
    "/*"[*!]    { yybegin(DATA_STATE); return DOC_DECO; }
    "*"+ "/"    { return (isLastToken() ? DOC_DECO : DOC_TEXT); }
    "*"         { yybegin(DATA_STATE); return DOC_DECO; }
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// EOL docs
///////////////////////////////////////////////////////////////////////////////////////////////////

<IN_EOL> "//"[/!]   { yybegin(DATA_STATE); return DOC_DECO; }

///////////////////////////////////////////////////////////////////////////////////////////////////
// Doc contents
///////////////////////////////////////////////////////////////////////////////////////////////////

<IN_DOC_DATA> {
    //== Headings
    //== http://spec.commonmark.org/0.25/#atx-headings
    {HEADING_HASH} " " [^\r\n]+     { yybegin(IN_DOC_DATA_DEEP); docHeadingTrimRight(); return DOC_HEADING; }
    {HEADING_HASH} [\ \r\n]         { yybegin(IN_DOC_DATA_DEEP); yypushback(1); return DOC_HEADING; }
    {HEADING_HASH}                  { if(isLastToken()) { return DOC_HEADING; }
                                      else { yybegin(IN_DOC_DATA_DEEP); return DOC_TEXT; } }

    {LINE_WS}+                      { return WHITE_SPACE; }
    [^]                             { yybegin(IN_DOC_DATA_DEEP); yypushback(1); }
}

<IN_DOC_DATA_DEEP> {
    "*"+ "/"            {
        if(MAIN_STATE == IN_BLOCK && isLastToken()) { yybegin(MAIN_STATE); yypushback(yylength()); }
        else { return DOC_TEXT; }
    }

    {INLINE_LINK}       { return DOC_INLINE_LINK; }
    {REF_LINK}          { return DOC_REF_LINK; }
    {LINK_REF_DEF}      { return DOC_LINK_REF_DEF; }

    {EOL_WS}            { yybegin(MAIN_STATE); return WHITE_SPACE;}
    {LINE_WS}+          { return WHITE_SPACE; }

    [^]                 { return DOC_TEXT; }
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Catch All
///////////////////////////////////////////////////////////////////////////////////////////////////

{WHITE_SPACE_CHAR}  { return WHITE_SPACE; }
[^]                 { return BAD_CHARACTER; }
