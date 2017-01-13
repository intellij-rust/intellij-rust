package org.rust.lang.core.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.rust.lang.core.lexer.RustEscapesLexer;

public interface RsTokenElementTypes {

    // Keywords

    RsTokenType ABSTRACT = new RsTokenType("abstract");
    RsTokenType ALIGNOF = new RsTokenType("alignof");
    RsTokenType AS = new RsTokenType("as");
    RsTokenType BECOME = new RsTokenType("become");
    RsTokenType BOX = new RsTokenType("box");
    RsTokenType BREAK = new RsTokenType("break");
    RsTokenType CONST = new RsTokenType("const");
    RsTokenType CONTINUE = new RsTokenType("continue");
    RsTokenType CRATE = new RsTokenType("crate");
    RsTokenType CSELF = new RsTokenType("Self");
    RsTokenType DO = new RsTokenType("do");
    RsTokenType ELSE = new RsTokenType("else");
    RsTokenType ENUM = new RsTokenType("enum");
    RsTokenType EXTERN = new RsTokenType("extern");
    RsTokenType FALSE = new RsTokenType("false");
    RsTokenType FINAL = new RsTokenType("final");
    RsTokenType FN = new RsTokenType("fn");
    RsTokenType FOR = new RsTokenType("for");
    RsTokenType IF = new RsTokenType("if");
    RsTokenType IMPL = new RsTokenType("impl");
    RsTokenType IN = new RsTokenType("in");
    RsTokenType LET = new RsTokenType("let");
    RsTokenType LOOP = new RsTokenType("loop");
    RsTokenType KW_MACRO = new RsTokenType("macro");
    RsTokenType MATCH = new RsTokenType("match");
    RsTokenType MOD = new RsTokenType("mod");
    RsTokenType MOVE = new RsTokenType("move");
    RsTokenType MUT = new RsTokenType("mut");
    RsTokenType OFFSETOF = new RsTokenType("offsetof");
    RsTokenType OVERRIDE = new RsTokenType("override");
    RsTokenType PRIV = new RsTokenType("priv");
    RsTokenType PROC = new RsTokenType("proc");
    RsTokenType PUB = new RsTokenType("pub");
    RsTokenType PURE = new RsTokenType("pure");
    RsTokenType REF = new RsTokenType("ref");
    RsTokenType RETURN = new RsTokenType("return");
    RsTokenType SELF = new RsTokenType("self");
    RsTokenType SIZEOF = new RsTokenType("sizeof");
    RsTokenType STATIC = new RsTokenType("static");
    RsTokenType STRUCT = new RsTokenType("struct");
    RsTokenType SUPER = new RsTokenType("super");
    RsTokenType TRAIT = new RsTokenType("trait");
    RsTokenType TRUE = new RsTokenType("true");
    RsTokenType TYPE_KW = new RsTokenType("type");
    RsTokenType TYPEOF = new RsTokenType("typeof");
    RsTokenType UNSAFE = new RsTokenType("unsafe");
    RsTokenType UNSIZED = new RsTokenType("unsized");
    RsTokenType USE = new RsTokenType("use");
    RsTokenType VIRTUAL = new RsTokenType("virtual");
    RsTokenType WHERE = new RsTokenType("where");
    RsTokenType WHILE = new RsTokenType("while");
    RsTokenType YIELD = new RsTokenType("yield");

    // Context keywords
    RsTokenType DEFAULT = new RsTokenType("default");
    RsTokenType UNION = new RsTokenType("union");

    // Identifiers

    RsTokenType IDENTIFIER = new RsTokenType("<IDENTIFIER>");
    RsTokenType LIFETIME = new RsTokenType("<LIFETIME>");
    RsTokenType UNDERSCORE = new RsTokenType("_");

    // Literals

    RsTokenType INTEGER_LITERAL = new RsTokenType("<INTEGER>");
    RsTokenType FLOAT_LITERAL = new RsTokenType("<FLOAT>");
    RsTokenType BYTE_LITERAL = new RsTokenType("<BYTE>");
    RsTokenType CHAR_LITERAL = new RsTokenType("<CHAR>");
    RsTokenType STRING_LITERAL = new RsTokenType("<STRING>");
    RsTokenType BYTE_STRING_LITERAL = new RsTokenType("<BYTE_STRING>");
    RsTokenType RAW_STRING_LITERAL = new RsTokenType("<RAW_STRING>");
    RsTokenType RAW_BYTE_STRING_LITERAL = new RsTokenType("<RAW_BYTE_STRING>");

    // Comments

    RsTokenType BLOCK_COMMENT = new RsTokenType("<BLOCK_COMMENT>");
    RsTokenType EOL_COMMENT = new RsTokenType("<EOL_COMMENT>");

    RsTokenType INNER_BLOCK_DOC_COMMENT = new RsTokenType("<INNER_BLOCK_DOC_COMMENT>");
    RsTokenType OUTER_BLOCK_DOC_COMMENT = new RsTokenType("<OUTER_BLOCK_DOC_COMMENT>");
    RsTokenType INNER_EOL_DOC_COMMENT = new RsTokenType("<INNER_EOL_DOC_COMMENT>");
    RsTokenType OUTER_EOL_DOC_COMMENT = new RsTokenType("<OUTER_EOL_DOC_COMMENT>");

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

    RsTokenType AND = new RsTokenType("&");
    RsTokenType ANDEQ = new RsTokenType("&=");
    RsTokenType ARROW = new RsTokenType("->");
    RsTokenType FAT_ARROW = new RsTokenType("=>");
    RsTokenType SHA = new RsTokenType("#");
    RsTokenType COLON = new RsTokenType(":");
    RsTokenType COLONCOLON = new RsTokenType("::");
    RsTokenType COMMA = new RsTokenType(",");
    RsTokenType DIV = new RsTokenType("/");
    RsTokenType DIVEQ = new RsTokenType("/=");
    RsTokenType DOT = new RsTokenType(".");
    RsTokenType DOTDOT = new RsTokenType("..");
    RsTokenType DOTDOTDOT = new RsTokenType("...");
    RsTokenType EQ = new RsTokenType("=");
    RsTokenType EQEQ = new RsTokenType("==");
    RsTokenType EXCL = new RsTokenType("!");
    RsTokenType EXCLEQ = new RsTokenType("!=");
    RsTokenType GT = new RsTokenType(">");
    RsTokenType LT = new RsTokenType("<");
    RsTokenType MINUS = new RsTokenType("-");
    RsTokenType MINUSEQ = new RsTokenType("-=");
    RsTokenType MUL = new RsTokenType("*");
    RsTokenType MULEQ = new RsTokenType("*=");
    RsTokenType OR = new RsTokenType("|");
    RsTokenType OREQ = new RsTokenType("|=");
    RsTokenType PLUS = new RsTokenType("+");
    RsTokenType PLUSEQ = new RsTokenType("+=");
    RsTokenType REM = new RsTokenType("%");
    RsTokenType REMEQ = new RsTokenType("%=");
    RsTokenType SEMICOLON = new RsTokenType(";");
    RsTokenType XOR = new RsTokenType("^");
    RsTokenType XOREQ = new RsTokenType("^=");
    RsTokenType Q = new RsTokenType("?");
    RsTokenType AT = new RsTokenType("@");
    RsTokenType DOLLAR = new RsTokenType("$");

    //
    // Operators created in parser by collapsing
    //

    RsTokenType GTGTEQ = new RsTokenType(">>=");
    RsTokenType GTGT = new RsTokenType(">>");
    RsTokenType GTEQ = new RsTokenType(">=");
    RsTokenType LTLTEQ = new RsTokenType("<<=");
    RsTokenType LTLT = new RsTokenType("<<");
    RsTokenType LTEQ = new RsTokenType("<=");
    RsTokenType OROR = new RsTokenType("||");
    RsTokenType ANDAND = new RsTokenType("&&");

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

    @NotNull
    TokenSet STRING_LITERALS = TokenSet.create(STRING_LITERAL, BYTE_STRING_LITERAL);

    @NotNull
    TokenSet RAW_LITERALS = TokenSet.create(RAW_STRING_LITERAL, RAW_BYTE_STRING_LITERAL);

    @NotNull
    TokenSet CONTEXTUAL_KEYWORDS = TokenSet.create(DEFAULT, UNION);
}
