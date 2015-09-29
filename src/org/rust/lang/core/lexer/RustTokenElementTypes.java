package org.rust.lang.core.lexer;

import com.intellij.psi.tree.IElementType;

public interface RustTokenElementTypes {

    // Keywords

    IElementType ABSTRACT   = new RustKeywordTokenType("abstract");
    IElementType ALIGNOF    = new RustKeywordTokenType("alignof");
    IElementType AS         = new RustKeywordTokenType("as");
    IElementType BECOME     = new RustKeywordTokenType("become");
    IElementType BOX        = new RustKeywordTokenType("box");
    IElementType BREAK      = new RustKeywordTokenType("break");
    IElementType CONST      = new RustKeywordTokenType("const");
    IElementType CONTINUE   = new RustKeywordTokenType("continue");
    IElementType CRATE      = new RustKeywordTokenType("crate");
    IElementType CSELF      = new RustKeywordTokenType("Self");
    IElementType ELSE       = new RustKeywordTokenType("else");
    IElementType ENUM       = new RustKeywordTokenType("enum");
    IElementType DO         = new RustKeywordTokenType("do");
    IElementType EXTERN     = new RustKeywordTokenType("extern");
    IElementType FALSE      = new RustKeywordTokenType("false");
    IElementType FINAL      = new RustKeywordTokenType("final");
    IElementType FN         = new RustKeywordTokenType("fn");
    IElementType FOR        = new RustKeywordTokenType("for");
    IElementType IF         = new RustKeywordTokenType("if");
    IElementType IMPL       = new RustKeywordTokenType("impl");
    IElementType IN         = new RustKeywordTokenType("in");
    IElementType LET        = new RustKeywordTokenType("let");
    IElementType LOOP       = new RustKeywordTokenType("loop");
    IElementType MACRO      = new RustKeywordTokenType("macro");
    IElementType MATCH      = new RustKeywordTokenType("match");
    IElementType MOD        = new RustKeywordTokenType("mod");
    IElementType MOVE       = new RustKeywordTokenType("move");
    IElementType MUT        = new RustKeywordTokenType("mut");
    IElementType NUMBER     = new RustKeywordTokenType("number");
    IElementType OFFSETOF   = new RustKeywordTokenType("offsetof");
    IElementType OVERRIDE   = new RustKeywordTokenType("override");
    IElementType PRIV       = new RustKeywordTokenType("priv");
    IElementType PROC       = new RustKeywordTokenType("proc");
    IElementType PUB        = new RustKeywordTokenType("pub");
    IElementType PURE       = new RustKeywordTokenType("pure");
    IElementType REF        = new RustKeywordTokenType("ref");
    IElementType RETURN     = new RustKeywordTokenType("return");
    IElementType SELF       = new RustKeywordTokenType("self");
    IElementType SIZEOF     = new RustKeywordTokenType("sizeof");
    IElementType STATIC     = new RustKeywordTokenType("static");
    IElementType STRUCT     = new RustKeywordTokenType("struct");
    IElementType SUPER      = new RustKeywordTokenType("super");
    IElementType TRAIT      = new RustKeywordTokenType("trait");
    IElementType TRUE       = new RustKeywordTokenType("true");
    IElementType TYPE       = new RustKeywordTokenType("type");
    IElementType TYPEOF     = new RustKeywordTokenType("typeof");
    IElementType UNSAFE     = new RustKeywordTokenType("unsafe");
    IElementType UNSIZED    = new RustKeywordTokenType("unsized");
    IElementType USE        = new RustKeywordTokenType("use");
    IElementType VIRTUAL    = new RustKeywordTokenType("virtual");
    IElementType WHERE      = new RustKeywordTokenType("where");
    IElementType WHILE      = new RustKeywordTokenType("while");
    IElementType YIELD      = new RustKeywordTokenType("yield");

    // Identifiers

    IElementType IDENTIFIER = new RustTokenType("<IDENTIFIER>");
    IElementType LIFETIME = new RustTokenType("<LIFETIME>");
    IElementType STATIC_LIFETIME = new RustTokenType("<STATIC_LIFETIME>");

    // Literals

    IElementType INTEGER_LITERAL = new RustTokenType("<INTEGER>");
    IElementType FLOAT_LITERAL = new RustTokenType("<FLOAT>");
    IElementType STRING_LITERAL = new RustTokenType("<STRING>");
    IElementType CHAR_LITERAL = new RustTokenType("<CHAR>");
    IElementType BYTE_STRING_LITERAL = new RustTokenType("<BYTE_STRING>");
    IElementType BYTE_LITERAL = new RustTokenType("<BYTE>");

    // Comments

    IElementType BLOCK_COMMENT = new RustTokenType("<BLOCK_COMMENT>");
    IElementType EOL_COMMENT = new RustTokenType("<EOL_COMMENT>");

    IElementType INNER_DOC_COMMENT = new RustTokenType("<INNER_DOC_COMMENT>");
    IElementType OUTER_DOC_COMMENT = new RustTokenType("<OUTER_DOC_COMMENT>");

    IElementType SHEBANG_LINE = new RustTokenType("<SHEBANG_LINE>");

    // Operators

    IElementType AND = new RustTokenType("&");
    IElementType ANDAND = new RustTokenType("&&");
    IElementType ANDEQ = new RustTokenType("&=");
    IElementType ARROW = new RustTokenType("->");
    IElementType FAT_ARROW = new RustTokenType("=>");
    IElementType SHA = new RustTokenType("#");
    IElementType SHEBANG = new RustTokenType("#!");
    IElementType COLON = new RustTokenType(":");
    IElementType COLONCOLON = new RustTokenType("::");
    IElementType COMMA = new RustTokenType(",");
    IElementType DIV = new RustTokenType("/");
    IElementType DIVEQ = new RustTokenType("/=");
    IElementType DOT = new RustTokenType(".");
    IElementType DOTDOT = new RustTokenType("..");
    IElementType DOTDOTDOT = new RustTokenType("...");
    IElementType ELLIPSIS = new RustTokenType("...");
    IElementType EQ = new RustTokenType("=");
    IElementType EQEQ = new RustTokenType("==");
    IElementType EXCL = new RustTokenType("!");
    IElementType EXCLEQ = new RustTokenType("!=");
    IElementType GT = new RustTokenType(">");
    IElementType LBRACE = new RustTokenType("{");
    IElementType LBRACK = new RustTokenType("[");
    IElementType LPAREN = new RustTokenType("(");
    IElementType LT = new RustTokenType("<");
    IElementType LTEQ = new RustTokenType("<=");
    IElementType LTLT = new RustTokenType("<<");
    IElementType LTLTEQ = new RustTokenType("<<=");
    IElementType MINUS = new RustTokenType("-");
    IElementType MINUSEQ = new RustTokenType("-=");
    IElementType MINUSMINUS = new RustTokenType("--");
    IElementType MUL = new RustTokenType("*");
    IElementType MULEQ = new RustTokenType("*=");
    IElementType OR = new RustTokenType("|");
    IElementType OREQ = new RustTokenType("|=");
    IElementType OROR = new RustTokenType("||");
    IElementType PLUS = new RustTokenType("+");
    IElementType PLUSEQ = new RustTokenType("+=");
    IElementType PLUSPLUS = new RustTokenType("++");
    IElementType RBRACE = new RustTokenType("}");
    IElementType RBRACK = new RustTokenType("]");
    IElementType REM = new RustTokenType("%");
    IElementType REMEQ = new RustTokenType("%=");
    IElementType RPAREN = new RustTokenType(")");
    IElementType SEMICOLON = new RustTokenType(";");
    IElementType XOR = new RustTokenType("^");
    IElementType XOREQ = new RustTokenType("^=");
    IElementType Q = new RustTokenType("?");
    IElementType AT = new RustTokenType("@");
    IElementType UNDERSCORE = new RustTokenType("_");
    IElementType DOLLAR = new RustTokenType("$");
}
