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
    RustTokenType UNDERSCORE = new RustTokenType("_");

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

    //
    // Grouping
    //

    RustTokenType LBRACE = new RustTokenType("{");
    RustTokenType LBRACK = new RustTokenType("[");
    RustTokenType LPAREN = new RustTokenType("(");
    RustTokenType RBRACE = new RustTokenType("}");
    RustTokenType RBRACK = new RustTokenType("]");
    RustTokenType RPAREN = new RustTokenType(")");

    // Operators

    RustTokenType AND = new RustOperatorTokenType("&");
    RustTokenType ANDEQ = new RustOperatorTokenType("&=");
    RustTokenType ARROW = new RustOperatorTokenType("->");
    RustTokenType FAT_ARROW = new RustOperatorTokenType("=>");
    RustTokenType SHA = new RustOperatorTokenType("#");
    RustTokenType COLON = new RustOperatorTokenType(":");
    RustTokenType COLONCOLON = new RustOperatorTokenType("::");
    RustTokenType COMMA = new RustOperatorTokenType(",");
    RustTokenType DIV = new RustOperatorTokenType("/");
    RustTokenType DIVEQ = new RustOperatorTokenType("/=");
    RustTokenType DOT = new RustOperatorTokenType(".");
    RustTokenType DOTDOT = new RustOperatorTokenType("..");
    RustTokenType DOTDOTDOT = new RustOperatorTokenType("...");
    RustTokenType EQ = new RustOperatorTokenType("=");
    RustTokenType EQEQ = new RustOperatorTokenType("==");
    RustTokenType EXCL = new RustOperatorTokenType("!");
    RustTokenType EXCLEQ = new RustOperatorTokenType("!=");
    RustTokenType GT = new RustOperatorTokenType(">");
    RustTokenType LT = new RustOperatorTokenType("<");
    RustTokenType MINUS = new RustOperatorTokenType("-");
    RustTokenType MINUSEQ = new RustOperatorTokenType("-=");
    RustTokenType MUL = new RustOperatorTokenType("*");
    RustTokenType MULEQ = new RustOperatorTokenType("*=");
    RustTokenType OR = new RustOperatorTokenType("|");
    RustTokenType OREQ = new RustOperatorTokenType("|=");
    RustTokenType PLUS = new RustOperatorTokenType("+");
    RustTokenType PLUSEQ = new RustOperatorTokenType("+=");
    RustTokenType REM = new RustOperatorTokenType("%");
    RustTokenType REMEQ = new RustOperatorTokenType("%=");
    RustTokenType SEMICOLON = new RustOperatorTokenType(";");
    RustTokenType XOR = new RustOperatorTokenType("^");
    RustTokenType XOREQ = new RustOperatorTokenType("^=");
    RustTokenType Q = new RustOperatorTokenType("?");
    RustTokenType AT = new RustOperatorTokenType("@");
    RustTokenType DOLLAR = new RustOperatorTokenType("$");

    //
    // Operators created in parser by collapsing
    //

    RustTokenType GTGTEQ = new RustOperatorTokenType(">>=");
    RustTokenType GTGT = new RustOperatorTokenType(">>");
    RustTokenType GTEQ = new RustOperatorTokenType(">=");
    RustTokenType LTLTEQ = new RustOperatorTokenType("<<=");
    RustTokenType LTLT = new RustOperatorTokenType("<<");
    RustTokenType LTEQ = new RustOperatorTokenType("<=");
    RustTokenType OROR = new RustOperatorTokenType("||");
    RustTokenType ANDAND = new RustOperatorTokenType("&&");

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

    @NotNull
    TokenSet ALL_OPS = TokenSet.create(
        IElementType.enumerate(
            new IElementType.Predicate() {
                @Override
                public boolean matches(@NotNull IElementType type) {
                    return type instanceof RustOperatorTokenType;
                }
            }
        )
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

    @NotNull
    TokenSet STRING_LITERALS = TokenSet.create(STRING_LITERAL, BYTE_STRING_LITERAL);

    @NotNull
    TokenSet RAW_LITERALS = TokenSet.create(RAW_STRING_LITERAL, RAW_BYTE_STRING_LITERAL);
}
