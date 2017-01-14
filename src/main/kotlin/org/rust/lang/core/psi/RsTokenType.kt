package org.rust.lang.core.psi

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.RsLanguage
import org.rust.lang.core.parser.RustParserDefinition.Companion.BLOCK_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.EOL_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_EOL_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.RsCompositeElementTypes.*

open class RsTokenType(debugName: String) : IElementType(debugName, RsLanguage)

private fun tokenSetOf(vararg tokens: IElementType) = TokenSet.create(*tokens)

val RS_KEYWORDS = tokenSetOf(
    ABSTRACT, ALIGNOF, AS,
    BECOME, BOX, BREAK,
    CONST, CONTINUE, CRATE, CSELF,
    DO, DEFAULT,
    ELSE, ENUM, EXTERN,
    FALSE, FINAL, FN, FOR,
    IF, IMPL, IN,
    MACRO_KW,
    LET, LOOP,
    MATCH, MOD, MOVE, MUT,
    OFFSETOF, OVERRIDE,
    PRIV, PROC, PUB, PURE,
    REF, RETURN,
    SELF, SIZEOF, STATIC, STRUCT, SUPER,
    TRAIT, TRUE, TYPE_KW, TYPEOF,
    UNION, UNSAFE, UNSIZED, USE,
    VIRTUAL,
    WHERE, WHILE,
    YIELD
)

val RS_OPERATORS = tokenSetOf(
    AND, ANDEQ, ARROW, FAT_ARROW, SHA, COLON, COLONCOLON, COMMA, DIV, DIVEQ, DOT, DOTDOT, DOTDOTDOT, EQ, EQEQ, EXCL,
    EXCLEQ, GT, LT, MINUS, MINUSEQ, MUL, MULEQ, OR, OREQ, PLUS, PLUSEQ, REM, REMEQ, SEMICOLON, XOR, XOREQ, Q, AT,
    DOLLAR, GTGTEQ, GTGT, GTEQ, LTLTEQ, LTLT, LTEQ, OROR, ANDAND
)

val RS_DOC_COMMENTS = tokenSetOf(
    INNER_BLOCK_DOC_COMMENT, OUTER_BLOCK_DOC_COMMENT,
    INNER_EOL_DOC_COMMENT, OUTER_EOL_DOC_COMMENT
)

val RS_COMMENTS = TokenSet.orSet(
    tokenSetOf(BLOCK_COMMENT, EOL_COMMENT),
    RS_DOC_COMMENTS)

val RS_EOL_COMMENTS = tokenSetOf(EOL_COMMENT, INNER_EOL_DOC_COMMENT, OUTER_EOL_DOC_COMMENT)

val RS_STRING_LITERALS = tokenSetOf(STRING_LITERAL, BYTE_STRING_LITERAL)

val RS_RAW_LITERALS = tokenSetOf(RAW_STRING_LITERAL, RAW_BYTE_STRING_LITERAL)

val RS_CONTEXTUAL_KEYWORDS = tokenSetOf(DEFAULT, UNION)
