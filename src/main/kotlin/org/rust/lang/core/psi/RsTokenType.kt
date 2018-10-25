/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

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
import org.rust.lang.core.psi.RsElementTypes.*

open class RsTokenType(debugName: String) : IElementType(debugName, RsLanguage)

fun tokenSetOf(vararg tokens: IElementType) = TokenSet.create(*tokens)

val RS_KEYWORDS = tokenSetOf(
    AS,
    BOX, BREAK,
    CONST, CONTINUE, CRATE, CSELF,
    DEFAULT,
    ELSE, ENUM, EXTERN,
    FN, FOR,
    IF, IMPL, IN,
    MACRO_KW,
    LET, LOOP,
    MATCH, MOD, MOVE, MUT,
    PUB,
    REF, RETURN,
    SELF, STATIC, STRUCT, SUPER,
    TRAIT, TYPE_KW,
    UNION, UNSAFE, USE,
    WHERE, WHILE,
    YIELD
)

val RS_OPERATORS = tokenSetOf(
    AND, ANDEQ, ARROW, FAT_ARROW, SHA, COLON, COLONCOLON, COMMA, DIV, DIVEQ, DOT, DOTDOT, DOTDOTDOT, DOTDOTEQ, EQ, EQEQ, EXCL,
    EXCLEQ, GT, LT, MINUS, MINUSEQ, MUL, MULEQ, OR, OREQ, PLUS, PLUSEQ, REM, REMEQ, SEMICOLON, XOR, XOREQ, Q, AT,
    DOLLAR, GTGTEQ, GTGT, GTEQ, LTLTEQ, LTLT, LTEQ, OROR, ANDAND
)

val RS_BINARY_OPS = tokenSetOf(
    AND, ANDEQ, ANDAND,
    DIV, DIVEQ,
    EQ, EQEQ, EXCLEQ,
    GT, GTGT, GTEQ, GTGTEQ,
    LT, LTLT, LTEQ, LTLTEQ,
    MINUS, MINUSEQ, MUL, MULEQ,
    OR, OREQ, OROR,
    PLUS, PLUSEQ,
    REM, REMEQ,
    XOR, XOREQ
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

val RS_ALL_STRING_LITERALS = tokenSetOf(STRING_LITERAL, BYTE_STRING_LITERAL,
    RAW_STRING_LITERAL, RAW_BYTE_STRING_LITERAL)

val RS_LITERALS = tokenSetOf(STRING_LITERAL, BYTE_STRING_LITERAL, RAW_STRING_LITERAL, RAW_BYTE_STRING_LITERAL,
    CHAR_LITERAL, BYTE_LITERAL, INTEGER_LITERAL, FLOAT_LITERAL, BOOL_LITERAL)

val RS_CONTEXTUAL_KEYWORDS = tokenSetOf(DEFAULT, UNION, AUTO, DYN)

val RS_LIST_OPEN_SYMBOLS = tokenSetOf(LPAREN, LT)
val RS_LIST_CLOSE_SYMBOLS = tokenSetOf(RPAREN, GT)

val RS_BLOCK_LIKE_EXPRESSIONS = tokenSetOf(WHILE_EXPR, IF_EXPR, FOR_EXPR, LOOP_EXPR, MATCH_EXPR, BLOCK_EXPR)
