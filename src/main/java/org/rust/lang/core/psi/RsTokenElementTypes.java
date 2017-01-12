package org.rust.lang.core.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.rust.lang.core.lexer.RustEscapesLexer;
import org.rust.lang.core.psi.impl.RsNumericLiteralImpl;
import org.rust.lang.core.psi.impl.RsRawStringLiteralImpl;
import org.rust.lang.core.psi.impl.RsStringLiteralImpl;

public interface RsTokenElementTypes {

    // Keywords

    RsTokenType ABSTRACT = new RsKeywordTokenType("abstract");
    RsTokenType ALIGNOF = new RsKeywordTokenType("alignof");
    RsTokenType AS = new RsKeywordTokenType("as");
    RsTokenType BECOME = new RsKeywordTokenType("become");
    RsTokenType BOX = new RsKeywordTokenType("box");
    RsTokenType BREAK = new RsKeywordTokenType("break");
    RsTokenType CONST = new RsKeywordTokenType("const");
    RsTokenType CONTINUE = new RsKeywordTokenType("continue");
    RsTokenType CRATE = new RsKeywordTokenType("crate");
    RsTokenType CSELF = new RsKeywordTokenType("Self");
    RsTokenType DO = new RsKeywordTokenType("do");
    RsTokenType ELSE = new RsKeywordTokenType("else");
    RsTokenType ENUM = new RsKeywordTokenType("enum");
    RsTokenType EXTERN = new RsKeywordTokenType("extern");
    RsTokenType FALSE = new RsKeywordTokenType("false");
    RsTokenType FINAL = new RsKeywordTokenType("final");
    RsTokenType FN = new RsKeywordTokenType("fn");
    RsTokenType FOR = new RsKeywordTokenType("for");
    RsTokenType IF = new RsKeywordTokenType("if");
    RsTokenType IMPL = new RsKeywordTokenType("impl");
    RsTokenType IN = new RsKeywordTokenType("in");
    RsTokenType LET = new RsKeywordTokenType("let");
    RsTokenType LOOP = new RsKeywordTokenType("loop");
    RsTokenType KW_MACRO = new RsKeywordTokenType("macro");
    RsTokenType MATCH = new RsKeywordTokenType("match");
    RsTokenType MOD = new RsKeywordTokenType("mod");
    RsTokenType MOVE = new RsKeywordTokenType("move");
    RsTokenType MUT = new RsKeywordTokenType("mut");
    RsTokenType OFFSETOF = new RsKeywordTokenType("offsetof");
    RsTokenType OVERRIDE = new RsKeywordTokenType("override");
    RsTokenType PRIV = new RsKeywordTokenType("priv");
    RsTokenType PROC = new RsKeywordTokenType("proc");
    RsTokenType PUB = new RsKeywordTokenType("pub");
    RsTokenType PURE = new RsKeywordTokenType("pure");
    RsTokenType REF = new RsKeywordTokenType("ref");
    RsTokenType RETURN = new RsKeywordTokenType("return");
    RsTokenType SELF = new RsKeywordTokenType("self");
    RsTokenType SIZEOF = new RsKeywordTokenType("sizeof");
    RsTokenType STATIC = new RsKeywordTokenType("static");
    RsTokenType STRUCT = new RsKeywordTokenType("struct");
    RsTokenType SUPER = new RsKeywordTokenType("super");
    RsTokenType TRAIT = new RsKeywordTokenType("trait");
    RsTokenType TRUE = new RsKeywordTokenType("true");
    RsTokenType TYPE_KW = new RsKeywordTokenType("type");
    RsTokenType TYPEOF = new RsKeywordTokenType("typeof");
    RsTokenType UNSAFE = new RsKeywordTokenType("unsafe");
    RsTokenType UNSIZED = new RsKeywordTokenType("unsized");
    RsTokenType USE = new RsKeywordTokenType("use");
    RsTokenType VIRTUAL = new RsKeywordTokenType("virtual");
    RsTokenType WHERE = new RsKeywordTokenType("where");
    RsTokenType WHILE = new RsKeywordTokenType("while");
    RsTokenType YIELD = new RsKeywordTokenType("yield");

    // Context keywords
    RsTokenType DEFAULT = new RsKeywordTokenType("default");
    RsTokenType UNION = new RsKeywordTokenType("union");

    // Identifiers

    RsTokenType IDENTIFIER = new RsTokenType("<IDENTIFIER>");
    RsTokenType LIFETIME = new RsTokenType("<LIFETIME>");
    RsTokenType UNDERSCORE = new RsTokenType("_");

    // Literals

    RsTokenType INTEGER_LITERAL = RsNumericLiteralImpl.createTokenType("<INTEGER>");
    RsTokenType FLOAT_LITERAL = RsNumericLiteralImpl.createTokenType("<FLOAT>");
    RsTokenType BYTE_LITERAL = RsStringLiteralImpl.createTokenType("<BYTE>");
    RsTokenType CHAR_LITERAL = RsStringLiteralImpl.createTokenType("<CHAR>");
    RsTokenType STRING_LITERAL = RsStringLiteralImpl.createTokenType("<STRING>");
    RsTokenType BYTE_STRING_LITERAL = RsStringLiteralImpl.createTokenType("<BYTE_STRING>");
    RsTokenType RAW_STRING_LITERAL = RsRawStringLiteralImpl.createTokenType("<RAW_STRING>");
    RsTokenType RAW_BYTE_STRING_LITERAL = RsRawStringLiteralImpl.createTokenType("<RAW_BYTE_STRING>");

    // Comments

    RsTokenType BLOCK_COMMENT = new RsCommentTokenType("<BLOCK_COMMENT>");
    RsTokenType EOL_COMMENT = new RsCommentTokenType("<EOL_COMMENT>");

    RsTokenType INNER_BLOCK_DOC_COMMENT = new RsCommentTokenType("<INNER_BLOCK_DOC_COMMENT>");
    RsTokenType OUTER_BLOCK_DOC_COMMENT = new RsCommentTokenType("<OUTER_BLOCK_DOC_COMMENT>");
    RsTokenType INNER_EOL_DOC_COMMENT = new RsCommentTokenType("<INNER_EOL_DOC_COMMENT>");
    RsTokenType OUTER_EOL_DOC_COMMENT = new RsCommentTokenType("<OUTER_EOL_DOC_COMMENT>");

    RsTokenType SHEBANG_LINE = new RsTokenType("<SHEBANG_LINE>");

    //
    // Grouping
    //

    RsTokenType LBRACE = new RsTokenType("{");
    RsTokenType LBRACK = new RsTokenType("[");
    RsTokenType LPAREN = new RsTokenType("(");
    RsTokenType RBRACE = new RsTokenType("}");
    RsTokenType RBRACK = new RsTokenType("]");
    RsTokenType RPAREN = new RsTokenType(")");

    // Operators

    RsTokenType AND = new RsOperatorTokenType("&");
    RsTokenType ANDEQ = new RsOperatorTokenType("&=");
    RsTokenType ARROW = new RsOperatorTokenType("->");
    RsTokenType FAT_ARROW = new RsOperatorTokenType("=>");
    RsTokenType SHA = new RsOperatorTokenType("#");
    RsTokenType COLON = new RsOperatorTokenType(":");
    RsTokenType COLONCOLON = new RsOperatorTokenType("::");
    RsTokenType COMMA = new RsOperatorTokenType(",");
    RsTokenType DIV = new RsOperatorTokenType("/");
    RsTokenType DIVEQ = new RsOperatorTokenType("/=");
    RsTokenType DOT = new RsOperatorTokenType(".");
    RsTokenType DOTDOT = new RsOperatorTokenType("..");
    RsTokenType DOTDOTDOT = new RsOperatorTokenType("...");
    RsTokenType EQ = new RsOperatorTokenType("=");
    RsTokenType EQEQ = new RsOperatorTokenType("==");
    RsTokenType EXCL = new RsOperatorTokenType("!");
    RsTokenType EXCLEQ = new RsOperatorTokenType("!=");
    RsTokenType GT = new RsOperatorTokenType(">");
    RsTokenType LT = new RsOperatorTokenType("<");
    RsTokenType MINUS = new RsOperatorTokenType("-");
    RsTokenType MINUSEQ = new RsOperatorTokenType("-=");
    RsTokenType MUL = new RsOperatorTokenType("*");
    RsTokenType MULEQ = new RsOperatorTokenType("*=");
    RsTokenType OR = new RsOperatorTokenType("|");
    RsTokenType OREQ = new RsOperatorTokenType("|=");
    RsTokenType PLUS = new RsOperatorTokenType("+");
    RsTokenType PLUSEQ = new RsOperatorTokenType("+=");
    RsTokenType REM = new RsOperatorTokenType("%");
    RsTokenType REMEQ = new RsOperatorTokenType("%=");
    RsTokenType SEMICOLON = new RsOperatorTokenType(";");
    RsTokenType XOR = new RsOperatorTokenType("^");
    RsTokenType XOREQ = new RsOperatorTokenType("^=");
    RsTokenType Q = new RsOperatorTokenType("?");
    RsTokenType AT = new RsOperatorTokenType("@");
    RsTokenType DOLLAR = new RsOperatorTokenType("$");

    //
    // Operators created in parser by collapsing
    //

    RsTokenType GTGTEQ = new RsOperatorTokenType(">>=");
    RsTokenType GTGT = new RsOperatorTokenType(">>");
    RsTokenType GTEQ = new RsOperatorTokenType(">=");
    RsTokenType LTLTEQ = new RsOperatorTokenType("<<=");
    RsTokenType LTLT = new RsOperatorTokenType("<<");
    RsTokenType LTEQ = new RsOperatorTokenType("<=");
    RsTokenType OROR = new RsOperatorTokenType("||");
    RsTokenType ANDAND = new RsOperatorTokenType("&&");

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
                    return type instanceof RsOperatorTokenType;
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

    @NotNull
    TokenSet CONTEXTUAL_KEYWORDS = TokenSet.create(DEFAULT, UNION);
}
