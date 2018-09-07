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
    val ADJACENT_LINE_COMMENTS: WhitespacesAndCommentsBinder =
        WhitespacesAndCommentsBinder { tokens, _, getter ->
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
    fun checkStructAllowed(builder: PsiBuilder, level: Int): Boolean =
        BitUtil.isSet(builder.flags, STRUCT_ALLOWED)

    @JvmStatic
    fun checkTypeQualAllowed(builder: PsiBuilder, level: Int): Boolean =
        BitUtil.isSet(builder.flags, TYPE_QUAL_ALLOWED)

    @JvmStatic
    fun checkBraceAllowed(builder: PsiBuilder, level: Int): Boolean =
        builder.tokenType != LBRACE || checkStructAllowed(builder, level)

    @JvmStatic
    fun structLiterals(builder: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        builder.withFlag(STRUCT_ALLOWED, mode) { parser.parse(this, level) }

    @JvmStatic
    fun typeQuals(builder: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        builder.withFlag(TYPE_QUAL_ALLOWED, mode) { parser.parse(this, level) }

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
    fun stmtMode(builder: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        builder.withFlag(STMT_EXPR_MODE, mode) { parser.parse(this, level) }

    @JvmStatic
    fun isCompleteBlockExpr(builder: PsiBuilder, level: Int): Boolean =
        isBlock(builder, level) && BitUtil.isSet(builder.flags, STMT_EXPR_MODE)

    @JvmStatic
    fun isBlock(builder: PsiBuilder, level: Int): Boolean {
        val node = builder.latestDoneMarker ?: return false
        return node.tokenType in RS_BLOCK_LIKE_EXPRESSIONS || node.isBracedMacro(builder)
    }

    @JvmStatic
    fun pathMode(builder: PsiBuilder, level: Int, mode: PathParsingMode, parser: Parser): Boolean {
        val oldFlags = builder.flags
        val newFlags = setPathMod(oldFlags, mode)
        builder.flags = newFlags
        check(getPathMod(builder.flags) == mode)
        val result = parser.parse(builder, level)
        builder.flags = oldFlags
        return result
    }

    @JvmStatic
    fun isPathMode(builder: PsiBuilder, level: Int, mode: PathParsingMode): Boolean =
        mode == getPathMod(builder.flags)

    @JvmStatic
    fun unpairedToken(builder: PsiBuilder, level: Int): Boolean =
        when (builder.tokenType) {
            LBRACE, RBRACE,
            LPAREN, RPAREN,
            LBRACK, RBRACK -> false
            else -> {
                collapsedTokenType(builder)?.let { (tokenType, size) ->
                    val marker = builder.mark()
                    PsiBuilderUtil.advance(builder, size)
                    marker.collapse(tokenType)
                } ?: builder.advanceLexer()
                true
            }
        }

    @JvmStatic
    fun macroBindingGroupSeparatorToken(builder: PsiBuilder, level: Int): Boolean =
        when (builder.tokenType) {
            PLUS, MUL, Q -> false
            else -> unpairedToken(builder, level)
        }

    @JvmStatic
    fun macroSemicolon(builder: PsiBuilder, level: Int): Boolean {
        val node = builder.latestDoneMarker ?: return false
        builder.tokenText
        if (builder.originalText[node.endOffset - 1] == '}') return true
        return consumeToken(builder, SEMICOLON)
    }

    @JvmStatic
    fun macroIdentifier(builder: PsiBuilder, level: Int): Boolean =
        when (builder.tokenType) {
            TYPE_KW, CRATE, IDENTIFIER -> {
                builder.advanceLexer()
                true
            }
            else -> false
        }

    @JvmStatic
    fun tupleOrParenType(builder: PsiBuilder, level: Int, typeReference: Parser, tupleTypeUpper: Parser): Boolean {
        val tupleOrParens: PsiBuilder.Marker = enter_section_(builder)

        if (!consumeTokenSmart(builder, LPAREN)) {
            exit_section_(builder, tupleOrParens, null, false)
            return false
        }

        val firstType = enter_section_(builder)

        if (!typeReference.parse(builder, level)) {
            exit_section_(builder, firstType, null, false)
            exit_section_(builder, tupleOrParens, null, false)
            return false
        }
        if (consumeTokenFast(builder, RPAREN)) {
            exit_section_(builder, firstType, null, true)
            exit_section_(builder, tupleOrParens, null, true)
            return true
        }
        exit_section_(builder, firstType, TYPE_REFERENCE, true)
        val result = tupleTypeUpper.parse(builder, level)
        exit_section_(builder, tupleOrParens, TUPLE_TYPE, result)
        return result
    }

    @JvmStatic
    fun baseOrTraitType(
        builder: PsiBuilder,
        level: Int,
        pathP: Parser,
        implicitTraitTypeP: Parser,
        traitTypeUpperP: Parser
    ): Boolean {
        val baseOrTrait = enter_section_(builder)
        val polybound = enter_section_(builder)
        val bound = enter_section_(builder)
        val traitRef = enter_section_(builder)

        if (!pathP.parse(builder, level + 1)) {
            // May be it is lifetime `'a` or `for<'a>`
            exit_section_(builder, traitRef, null, false)
            exit_section_(builder, bound, null, false)
            exit_section_(builder, polybound, null, false)
            exit_section_(builder, baseOrTrait, null, false)
            return implicitTraitTypeP.parse(builder, level)
        }

        if (!nextTokenIs(builder, PLUS)) {
            exit_section_(builder, traitRef, null, true)
            exit_section_(builder, bound, null, true)
            exit_section_(builder, polybound, null, true)
            exit_section_(builder, baseOrTrait, BASE_TYPE, true)
            return true
        }

        exit_section_(builder, traitRef, TRAIT_REF, true)
        exit_section_(builder, bound, BOUND, true)
        exit_section_(builder, polybound, POLYBOUND, true)
        val result = traitTypeUpperP.parse(builder, level + 1)
        exit_section_(builder, baseOrTrait, TRAIT_TYPE, result)
        return result
    }

    @JvmStatic
    fun gtgteqImpl(builder: PsiBuilder, level: Int): Boolean =
        collapse(builder, GTGTEQ, GT, GT, EQ)

    @JvmStatic
    fun gtgtImpl(builder: PsiBuilder, level: Int): Boolean =
        collapse(builder, GTGT, GT, GT)

    @JvmStatic
    fun gteqImpl(builder: PsiBuilder, level: Int): Boolean =
        collapse(builder, GTEQ, GT, EQ)

    @JvmStatic
    fun ltlteqImpl(builder: PsiBuilder, level: Int): Boolean =
        collapse(builder, LTLTEQ, LT, LT, EQ)

    @JvmStatic
    fun ltltImpl(builder: PsiBuilder, level: Int): Boolean =
        collapse(builder, LTLT, LT, LT)

    @JvmStatic
    fun lteqImpl(builder: PsiBuilder, level: Int): Boolean =
        collapse(builder, LTEQ, LT, EQ)

    @JvmStatic
    fun ororImpl(builder: PsiBuilder, level: Int): Boolean =
        collapse(builder, OROR, OR, OR)

    @JvmStatic
    fun andandImpl(builder: PsiBuilder, level: Int): Boolean =
        collapse(builder, ANDAND, AND, AND)

    @JvmStatic
    fun defaultKeyword(builder: PsiBuilder, level: Int): Boolean =
        contextualKeyword(builder, "default", DEFAULT)

    @JvmStatic
    fun unionKeyword(builder: PsiBuilder, level: Int): Boolean =
        contextualKeyword(builder, "union", UNION)

    @JvmStatic
    fun autoKeyword(builder: PsiBuilder, level: Int): Boolean =
        contextualKeyword(builder, "auto", AUTO)

    @JvmStatic
    fun dynKeyword(builder: PsiBuilder, level: Int): Boolean =
        contextualKeyword(builder, "dyn", DYN)

    @JvmStatic
    private fun collapse(builder: PsiBuilder, tokenType: IElementType, vararg parts: IElementType): Boolean {
        // We do not want whitespace between parts, so firstly we do raw lookup for each part, and when we make sure
        // that we have desired token, we consume and collapse it.
        parts.forEachIndexed { i, tt ->
            if (builder.rawLookup(i) != tt) return false
        }
        val marker = builder.mark()
        PsiBuilderUtil.advance(builder, parts.size)
        marker.collapse(tokenType)
        return true
    }

    /** Collapses contextual tokens (like &&) to a single token. */
    fun collapsedTokenType(builder: PsiBuilder): Pair<IElementType, Int>? =
        when (builder.tokenType) {
            GT -> when (builder.rawLookup(1)) {
                GT -> when (builder.rawLookup(2)) {
                    EQ -> GTGTEQ to 3
                    else -> GTGT to 2
                }
                EQ -> GTEQ to 2
                else -> null
            }
            LT -> when (builder.rawLookup(1)) {
                LT -> when (builder.rawLookup(2)) {
                    EQ -> LTLTEQ to 3
                    else -> LTLT to 2
                }
                EQ -> LTEQ to 2
                else -> null
            }
            OR -> when (builder.rawLookup(1)) {
                OR -> OROR to 2
                else -> null
            }
            AND -> when (builder.rawLookup(1)) {
                AND -> ANDAND to 2
                else -> null
            }
            else -> null
        }

    private fun LighterASTNode.isBracedMacro(builder: PsiBuilder): Boolean {
        val openChar = builder.originalText
            .subSequence(startOffset, endOffset)
            .find { it == '{' || it == '[' || it == '(' }
        return tokenType == RsElementTypes.MACRO_EXPR && '{' == openChar
    }

    private fun contextualKeyword(builder: PsiBuilder, keyword: String, elementType: IElementType): Boolean {
        // Tricky: the token can be already remapped by some previous rule that was backtracked
        if ((builder.tokenType == IDENTIFIER
                && builder.tokenText == keyword
                && builder.lookAhead(1)?.equals(EXCL) != true)
            || builder.tokenType == elementType) {
            builder.remapCurrentToken(elementType)
            builder.advanceLexer()
            return true
        }
        return false
    }
}
