/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.lang.LighterASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderUtil
import com.intellij.lang.WhitespacesAndCommentsBinder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.util.Key
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.util.BitUtil
import org.rust.lang.core.parser.RustParserDefinition.Companion.EOL_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.RS_BLOCK_LIKE_EXPRESSIONS
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.stdext.makeBitMask

@Suppress("UNUSED_PARAMETER")
object RustParserUtil : GeneratedParserUtilBase() {
    enum class PathParsingMode { COLONS, NO_COLONS, NO_TYPES }
    enum class BinaryMode { ON, OFF }

    private val FLAGS: Key<Int> = Key("RustParserUtil.FLAGS")
    private var PsiBuilder.flags: Int
        get() = getUserData(FLAGS) ?: DEFAULT_FLAGS
        set(value) = putUserData(FLAGS, value)

    private fun PsiBuilder.withFlag(flag: Int, mode: BinaryMode, block: PsiBuilder.() -> Boolean): Boolean {
        val oldFlags = flags
        val newFlags = BitUtil.set(oldFlags, flag, mode == BinaryMode.ON)
        flags = newFlags
        val result = block()
        flags = oldFlags
        return result
    }


    private val STRUCT_ALLOWED: Int = makeBitMask(1)
    private val TYPE_QUAL_ALLOWED: Int = makeBitMask(2)
    private val STMT_EXPR_MODE: Int = makeBitMask(3)

    private val PATH_COLONS: Int = makeBitMask(4)
    private val PATH_NO_COLONS: Int = makeBitMask(5)
    private val PATH_NO_TYPES: Int = makeBitMask(6)
    private fun setPathMod(flags: Int, mode: PathParsingMode): Int {
        val flag = when (mode) {
            PathParsingMode.COLONS -> PATH_COLONS
            PathParsingMode.NO_COLONS -> PATH_NO_COLONS
            PathParsingMode.NO_TYPES -> PATH_NO_TYPES
        }
        return flags and (PATH_COLONS or PATH_NO_COLONS or PATH_NO_TYPES).inv() or flag
    }

    private fun getPathMod(flags: Int): PathParsingMode = when {
        BitUtil.isSet(flags, PATH_COLONS) -> PathParsingMode.COLONS
        BitUtil.isSet(flags, PATH_NO_COLONS) -> PathParsingMode.NO_COLONS
        BitUtil.isSet(flags, PATH_NO_TYPES) -> PathParsingMode.NO_TYPES
        else -> error("Path parsing mode not set")
    }

    private val DEFAULT_FLAGS: Int = STRUCT_ALLOWED or TYPE_QUAL_ALLOWED

    @JvmField
    val ADJACENT_LINE_COMMENTS = WhitespacesAndCommentsBinder { tokens, _, getter ->
        var candidate = tokens.size
        for (i in 0 until tokens.size) {
            val token = tokens[i]
            if (OUTER_BLOCK_DOC_COMMENT == token || OUTER_EOL_DOC_COMMENT == token) {
                candidate = minOf(candidate, i)
                break
            }
            if (EOL_COMMENT == token) {
                candidate = minOf(candidate, i)
            }
            if (TokenType.WHITE_SPACE == token && "\n\n" in getter[i]) {
                candidate = tokens.size
            }
        }
        candidate
    }

    //
    // Helpers
    //

    @JvmStatic
    fun checkStructAllowed(b: PsiBuilder, level: Int): Boolean = BitUtil.isSet(b.flags, STRUCT_ALLOWED)

    @JvmStatic
    fun checkTypeQualAllowed(b: PsiBuilder, level: Int): Boolean = BitUtil.isSet(b.flags, TYPE_QUAL_ALLOWED)

    @JvmStatic
    fun checkBraceAllowed(b: PsiBuilder, level: Int): Boolean =
        b.tokenType != LBRACE || checkStructAllowed(b, level)

    @JvmStatic
    fun structLiterals(b: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        b.withFlag(STRUCT_ALLOWED, mode) { parser.parse(this, level) }

    @JvmStatic
    fun typeQuals(b: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        b.withFlag(TYPE_QUAL_ALLOWED, mode) { parser.parse(this, level) }

    /**
     * Controls the difference between
     *
     *     ```
     *     // bit and
     *     let _ = {1} & x;
     *     ```
     * and
     *
     *     ```
     *     // two statements: block expression {1} and unary reference expression &x
     *     {1} & x;
     *     ```
     *
     * See `Restrictions::RESTRICTION_STMT_EXPR` in libsyntax
     */
    @JvmStatic
    fun stmtMode(b: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        b.withFlag(STMT_EXPR_MODE, mode) { parser.parse(this, level) }

    @JvmStatic
    fun isCompleteBlockExpr(b: PsiBuilder, level: Int): Boolean =
        isBlock(b, level) && BitUtil.isSet(b.flags, STMT_EXPR_MODE)

    @JvmStatic
    fun isBlock(b: PsiBuilder, level: Int): Boolean {
        val m = b.latestDoneMarker ?: return false
        return m.tokenType in RS_BLOCK_LIKE_EXPRESSIONS || m.isBracedMacro(b)
    }

    @JvmStatic
    fun pathMode(b: PsiBuilder, level: Int, mode: PathParsingMode, parser: Parser): Boolean {
        val oldFlags = b.flags
        val newFlags = setPathMod(oldFlags, mode)
        b.flags = newFlags
        check(getPathMod(b.flags) == mode)
        val result = parser.parse(b, level)
        b.flags = oldFlags
        return result
    }

    @JvmStatic
    fun isPathMode(b: PsiBuilder, level: Int, mode: PathParsingMode): Boolean =
        mode == getPathMod(b.flags)

    @JvmStatic
    fun unpairedToken(b: PsiBuilder, level: Int): Boolean =
        when (b.tokenType) {
            LBRACE, RBRACE -> false
            LPAREN, RPAREN -> false
            LBRACK, RBRACK -> false
            else -> {
                b.advanceLexer()
                true
            }
        }

    @JvmStatic
    fun macroSemicolon(b: PsiBuilder, level: Int): Boolean {
        val m = b.latestDoneMarker ?: return false
        b.tokenText
        if (b.originalText[m.endOffset - 1] == '}') return true
        return consumeToken(b, SEMICOLON)
    }

    @JvmStatic
    fun macroIdentifier(b: PsiBuilder, level: Int): Boolean =
        when (b.tokenType) {
            TYPE_KW, CRATE, IDENTIFIER -> {
                b.advanceLexer()
                true
            }
            else -> false
        }

    @JvmStatic
    fun tupleOrParenType(b: PsiBuilder, level: Int, typeReference: Parser, tupeTypeUpper: Parser): Boolean {
        val tupleOrParens: PsiBuilder.Marker = enter_section_(b)

        if (!consumeTokenSmart(b, LPAREN)) {
            exit_section_(b, tupleOrParens, null, false)
            return false
        }

        val firstType = enter_section_(b)

        if (!typeReference.parse(b, level)) {
            exit_section_(b, firstType, null, false)
            exit_section_(b, tupleOrParens, null, false)
            return false
        }
        if (consumeTokenFast(b, RPAREN)) {
            exit_section_(b, firstType, null, true)
            exit_section_(b, tupleOrParens, null, true)
            return true
        }
        exit_section_(b, firstType, TYPE_REFERENCE, true)
        val result = tupeTypeUpper.parse(b, level)
        exit_section_(b, tupleOrParens, TUPLE_TYPE, result)
        return result
    }

    @JvmStatic
    fun gtgteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, GTGTEQ, GT, GT, EQ)

    @JvmStatic
    fun gtgtImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, GTGT, GT, GT)

    @JvmStatic
    fun gteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, GTEQ, GT, EQ)

    @JvmStatic
    fun ltlteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, LTLTEQ, LT, LT, EQ)

    @JvmStatic
    fun ltltImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, LTLT, LT, LT)

    @JvmStatic
    fun lteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, LTEQ, LT, EQ)

    @JvmStatic
    fun ororImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, OROR, OR, OR)

    @JvmStatic
    fun andandImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, ANDAND, AND, AND)

    @JvmStatic
    fun defaultKeyword(b: PsiBuilder, level: Int): Boolean = contextualKeyword(b, "default", DEFAULT)

    @JvmStatic
    fun unionKeyword(b: PsiBuilder, level: Int): Boolean = contextualKeyword(b, "union", UNION)


    private @JvmStatic
    fun     collapse(b: PsiBuilder, tokenType: IElementType, vararg parts: IElementType): Boolean {
        // We do not want whitespace between parts, so firstly we do raw lookup for each part,
        // and when we make sure that we have desired token, we consume and collapse it.
        parts.forEachIndexed { i, tt ->
            if (b.rawLookup(i) != tt) return false
        }
        val marker = b.mark()
        PsiBuilderUtil.advance(b, parts.size)
        marker.collapse(tokenType)
        return true
    }

    private fun LighterASTNode.isBracedMacro(b: PsiBuilder): Boolean =
        tokenType == RsElementTypes.MACRO_EXPR &&
            '{' == b.originalText.subSequence(startOffset, endOffset).find { it == '{' || it == '[' || it == '(' }

    private fun contextualKeyword(b: PsiBuilder, keyword: String, elementType: IElementType): Boolean {
        // Tricky: the token can be already remapped by some previous rule that was backtracked
        if ((b.tokenType == IDENTIFIER && b.tokenText == keyword && b.lookAhead(1)?.equals(EXCL) != true) || b.tokenType == elementType) {
            b.remapCurrentToken(elementType)
            b.advanceLexer()
            return true
        }
        return false
    }

}

