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

  private boolean isLastToken() {
    return zzMarkedPos == zzEndRead;
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

%unicode

EOL_WS           = \n | \r | \r\n
LINE_WS          = [\ \t]
WHITE_SPACE_CHAR = {EOL_WS} | {LINE_WS}

%%

<YYINITIAL> {
    "/*"    { MAIN_STATE = IN_BLOCK; yybegin(IN_BLOCK); yypushback(2); }
    "//"    { MAIN_STATE = IN_EOL;   yybegin(IN_EOL);   yypushback(2); }
}

<IN_BLOCK> {
    "/*"[*!]    { yybegin(IN_DOC_DATA); return DOC_DECO; }
    "*"+ "/"    { return (isLastToken() ? DOC_DECO : DOC_TEXT); }
    "*"         { yybegin(IN_DOC_DATA); return DOC_DECO; }
}

<IN_EOL> "//"[/!]   { yybegin(IN_DOC_DATA); return DOC_DECO; }

<IN_DOC_DATA> {
    "*"+ "/"    {
        if(MAIN_STATE == IN_BLOCK && isLastToken()) { yybegin(MAIN_STATE); yypushback(yylength()); }
        else { return DOC_TEXT; }
    }

    {EOL_WS}    { yybegin(MAIN_STATE); return WHITE_SPACE;}
    {LINE_WS}+  { return WHITE_SPACE; }

    [^]         { return DOC_TEXT; }
}

{WHITE_SPACE_CHAR}  { return WHITE_SPACE; }
[^]                 { return BAD_CHARACTER; }
