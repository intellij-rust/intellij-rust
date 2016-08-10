package org.rust.lang.core.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.rust.lang.core.lexer.RustEscapesLexer;
import org.rust.lang.core.psi.impl.RustNumericLiteralImpl;
import org.rust.lang.core.psi.impl.RustRawStringLiteralImpl;
import org.rust.lang.core.psi.impl.RustStringLiteralImpl;

public interface RustTokenElementTypes {

    // Keywords

    RustTokenType ABSTRACT = new RustKeywordTokenType("abstract");
    RustTokenType ALIGNOF = new RustKeywordTokenType("alignof");
    RustTokenType AS = new RustKeywordTokenType("as");
    RustTokenType BECOME = new RustKeywordTokenType("become");
    RustTokenType BOX = new RustKeywordTokenType("box");
    RustTokenType BREAK = new RustKeywordTokenType("break");
    RustTokenType CONST = new RustKeywordTokenType("const");
    RustTokenType CONTINUE = new RustKeywordTokenType("continue");
    RustTokenType CRATE = new RustKeywordTokenType("crate");
    RustTokenType CSELF = new RustKeywordTokenType("Self");
    RustTokenType DO = new RustKeywordTokenType("do");
    RustTokenType ELSE = new RustKeywordTokenType("else");
    RustTokenType ENUM = new RustKeywordTokenType("enum");
    RustTokenType EXTERN = new RustKeywordTokenType("extern");
    RustTokenType FALSE = new RustKeywordTokenType("false");
    RustTokenType FINAL = new RustKeywordTokenType("final");
    RustTokenType FN = new RustKeywordTokenType("fn");
    RustTokenType FOR = new RustKeywordTokenType("for");
    RustTokenType IF = new RustKeywordTokenType("if");
    RustTokenType IMPL = new RustKeywordTokenType("impl");
    RustTokenType IN = new RustKeywordTokenType("in");
    RustTokenType LET = new RustKeywordTokenType("let");
    RustTokenType LOOP = new RustKeywordTokenType("loop");
    RustTokenType KW_MACRO = new RustKeywordTokenType("macro");
    RustTokenType MATCH = new RustKeywordTokenType("match");
    RustTokenType MOD = new RustKeywordTokenType("mod");
    RustTokenType MOVE = new RustKeywordTokenType("move");
    RustTokenType MUT = new RustKeywordTokenType("mut");
    RustTokenType OFFSETOF = new RustKeywordTokenType("offsetof");
    RustTokenType OVERRIDE = new RustKeywordTokenType("override");
    RustTokenType PRIV = new RustKeywordTokenType("priv");
    RustTokenType PROC = new RustKeywordTokenType("proc");
    RustTokenType PUB = new RustKeywordTokenType("pub");
    RustTokenType PURE = new RustKeywordTokenType("pure");
    RustTokenType REF = new RustKeywordTokenType("ref");
    RustTokenType RETURN = new RustKeywordTokenType("return");
    RustTokenType SELF = new RustKeywordTokenType("self");
    RustTokenType SIZEOF = new RustKeywordTokenType("sizeof");
    RustTokenType STATIC = new RustKeywordTokenType("static");
    RustTokenType STRUCT = new RustKeywordTokenType("struct");
    RustTokenType SUPER = new RustKeywordTokenType("super");
    RustTokenType TRAIT = new RustKeywordTokenType("trait");
    RustTokenType TRUE = new RustKeywordTokenType("true");
    RustTokenType TYPE_KW = new RustKeywordTokenType("type");
    RustTokenType TYPEOF = new RustKeywordTokenType("typeof");
    RustTokenType UNSAFE = new RustKeywordTokenType("unsafe");
    RustTokenType UNSIZED = new RustKeywordTokenType("unsized");
    RustTokenType USE = new RustKeywordTokenType("use");
    RustTokenType VIRTUAL = new RustKeywordTokenType("virtual");
    RustTokenType WHERE = new RustKeywordTokenType("where");
    RustTokenType WHILE = new RustKeywordTokenType("while");
    RustTokenType YIELD = new RustKeywordTokenType("yield");

    // Identifiers

    RustTokenType IDENTIFIER = new RustTokenType("<IDENTIFIER>");
    RustTokenType LIFETIME = new RustTokenType("<LIFETIME>");

    // Literals

    RustTokenType INTEGER_LITERAL = RustNumericLiteralImpl.createTokenType("<INTEGER>");
    RustTokenType FLOAT_LITERAL = RustNumericLiteralImpl.createTokenType("<FLOAT>");
    RustTokenType BYTE_LITERAL = RustStringLiteralImpl.createTokenType("<BYTE>");
    RustTokenType CHAR_LITERAL = RustStringLiteralImpl.createTokenType("<CHAR>");
    RustTokenType STRING_LITERAL = RustStringLiteralImpl.createTokenType("<STRING>");
    RustTokenType BYTE_STRING_LITERAL = RustStringLiteralImpl.createTokenType("<BYTE_STRING>");
    RustTokenType RAW_STRING_LITERAL = RustRawStringLiteralImpl.createTokenType("<RAW_STRING>");
    RustTokenType RAW_BYTE_STRING_LITERAL = RustRawStringLiteralImpl.createTokenType("<RAW_BYTE_STRING>");

    // Comments

    RustTokenType BLOCK_COMMENT = new RustCommentTokenType("<BLOCK_COMMENT>");
    RustTokenType EOL_COMMENT = new RustCommentTokenType("<EOL_COMMENT>");

    RustTokenType INNER_BLOCK_DOC_COMMENT = new RustCommentTokenType("<INNER_BLOCK_DOC_COMMENT>");
    RustTokenType OUTER_BLOCK_DOC_COMMENT = new RustCommentTokenType("<OUTER_BLOCK_DOC_COMMENT>");
    RustTokenType INNER_EOL_DOC_COMMENT = new RustCommentTokenType("<INNER_EOL_DOC_COMMENT>");
    RustTokenType OUTER_EOL_DOC_COMMENT = new RustCommentTokenType("<OUTER_EOL_DOC_COMMENT>");

    RustTokenType SHEBANG_LINE = new RustTokenType("<SHEBANG_LINE>");

    // Operators

    RustTokenType AND = new RustTokenType("&");
    RustTokenType ANDEQ = new RustTokenType("&=");
    RustTokenType ARROW = new RustTokenType("->");
    RustTokenType FAT_ARROW = new RustTokenType("=>");
    RustTokenType SHA = new RustTokenType("#");
    RustTokenType COLON = new RustTokenType(":");
    RustTokenType COLONCOLON = new RustTokenType("::");
    RustTokenType COMMA = new RustTokenType(",");
    RustTokenType DIV = new RustTokenType("/");
    RustTokenType DIVEQ = new RustTokenType("/=");
    RustTokenType DOT = new RustTokenType(".");
    RustTokenType DOTDOT = new RustTokenType("..");
    RustTokenType DOTDOTDOT = new RustTokenType("...");
    RustTokenType EQ = new RustTokenType("=");
    RustTokenType EQEQ = new RustTokenType("==");
    RustTokenType EXCL = new RustTokenType("!");
    RustTokenType EXCLEQ = new RustTokenType("!=");
    RustTokenType GT = new RustTokenType(">");
    RustTokenType LBRACE = new RustTokenType("{");
    RustTokenType LBRACK = new RustTokenType("[");
    RustTokenType LPAREN = new RustTokenType("(");
    RustTokenType LT = new RustTokenType("<");
    RustTokenType MINUS = new RustTokenType("-");
    RustTokenType MINUSEQ = new RustTokenType("-=");
    RustTokenType MUL = new RustTokenType("*");
    RustTokenType MULEQ = new RustTokenType("*=");
    RustTokenType OR = new RustTokenType("|");
    RustTokenType OREQ = new RustTokenType("|=");
    RustTokenType PLUS = new RustTokenType("+");
    RustTokenType PLUSEQ = new RustTokenType("+=");
    RustTokenType RBRACE = new RustTokenType("}");
    RustTokenType RBRACK = new RustTokenType("]");
    RustTokenType REM = new RustTokenType("%");
    RustTokenType REMEQ = new RustTokenType("%=");
    RustTokenType RPAREN = new RustTokenType(")");
    RustTokenType SEMICOLON = new RustTokenType(";");
    RustTokenType XOR = new RustTokenType("^");
    RustTokenType XOREQ = new RustTokenType("^=");
    RustTokenType Q = new RustTokenType("?");
    RustTokenType AT = new RustTokenType("@");
    RustTokenType UNDERSCORE = new RustTokenType("_");
    RustTokenType DOLLAR = new RustTokenType("$");

    //
    // Operators created in parser by collapsing
    //

    RustTokenType GTGTEQ = new RustTokenType(">>=");
    RustTokenType GTGT = new RustTokenType(">>");
    RustTokenType GTEQ = new RustTokenType(">=");
    RustTokenType LTLTEQ = new RustTokenType("<<=");
    RustTokenType LTLT = new RustTokenType("<<");
    RustTokenType LTEQ = new RustTokenType("<=");
    RustTokenType OROR = new RustTokenType("||");
    RustTokenType ANDAND = new RustTokenType("&&");

    //
    // Token Sets
    //

    @NotNull
    TokenSet DOC_COMMENTS_TOKEN_SET = TokenSet.create(
        INNER_BLOCK_DOC_COMMENT,
        OUTER_BLOCK_DOC_COMMENT,
        INNER_EOL_DOC_COMMENT,
        OUTER_EOL_DOC_COMMENT);

    @NotNull
    TokenSet COMMENTS_TOKEN_SET = TokenSet.orSet(
        TokenSet.create(BLOCK_COMMENT, EOL_COMMENT),
        DOC_COMMENTS_TOKEN_SET);

    @NotNull
    TokenSet EOL_COMMENTS_TOKEN_SET = TokenSet.create(EOL_COMMENT, INNER_EOL_DOC_COMMENT, OUTER_EOL_DOC_COMMENT);

    @NotNull
    TokenSet BINARY_OPS = TokenSet.create(
        AND,
        ANDEQ,
        DIV,
        DIVEQ,
        EQ,
        EQEQ,
        EXCLEQ,
        GT,
        LT,
        MINUS,
        MINUSEQ,
        MUL,
        MULEQ,
        OR,
        OREQ,
        PLUS,
        PLUSEQ,
        REM,
        REMEQ,
        XOR,
        XOREQ,
        GTGTEQ,
        GTGT,
        GTEQ,
        LTLTEQ,
        LTLT,
        LTEQ,
        OROR,
        ANDAND
    );

    /**
     * Set of possible arguments for {@link RustEscapesLexer.Companion#of(IElementType)}
     */
    @NotNull
    TokenSet ESCAPABLE_LITERALS_TOKEN_SET = TokenSet.create(
        BYTE_LITERAL,
        CHAR_LITERAL,
        STRING_LITERAL,
        BYTE_STRING_LITERAL
    );
}
