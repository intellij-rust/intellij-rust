package org.rust.lang.core.parser

import com.intellij.lang.*
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.psi.RustCompositeElementTypes
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.RustTokenType

@Suppress("UNUSED_PARAMETER")
object RustParserUtil : GeneratedParserUtilBase() {
    enum class PathParsingMode { COLONS, NO_COLONS, NO_TYPES_ALLOWED }

    private val STRUCT_ALLOWED: Key<Boolean> = Key("org.rust.STRUCT_ALLOWED")
    private val PATH_PARSING_MODE: Key<PathParsingMode> = Key("org.rust.PATH_PARSING_MODE")

    private val PsiBuilder.structAllowed: Boolean get() = getUserData(STRUCT_ALLOWED) ?: true

    private val PsiBuilder.pathParsingMode: PathParsingMode get() = requireNotNull(getUserData(PATH_PARSING_MODE)) {
        "Path context is not set. Be sure to call one of `withParsingMode...` functions"
    }

    @JvmField val DOC_COMMENT_BINDER: WhitespacesAndCommentsBinder = WhitespacesBinders.leadingCommentsBinder(
        TokenSet.create(OUTER_BLOCK_DOC_COMMENT, OUTER_EOL_DOC_COMMENT))

    //
    // Helpers
    //

    // Parses either a paren_expr (92) or a tuple_expr (92, ) by postponing the decision of
    // what exactly is parsed until `,` is (not) seen.
    @JvmStatic fun tupleOrParenExpr(builder: PsiBuilder, level: Int,
                                    anyExpr: Parser,
                                    tupleExprEnd: Parser,
                                    parenExprEnd: Parser): Boolean {
        if (!recursion_guard_(builder, level, "tupleOrParenExpr")) return false
        if (!nextTokenIsFast(builder, LPAREN)) return false
        val marker = enter_section_(builder)
        var result = consumeTokenFast(builder, LPAREN)
        result = result && anyExpr.parse(builder, level + 1)

        var hasComma = false
        result = result && if (nextTokenIsFast(builder, COMMA)) {
            hasComma = true
            tupleExprEnd.parse(builder, level + 1)
        } else {
            parenExprEnd.parse(builder, level + 1)
        }
        val elementType = if (hasComma) RustCompositeElementTypes.TUPLE_EXPR else RustCompositeElementTypes.PAREN_EXPR

        exit_section_(builder, marker, elementType, result)
        return result
    }

    @JvmStatic fun checkStructAllowed(b: PsiBuilder, level: Int): Boolean = b.structAllowed

    @JvmStatic fun checkBraceAllowed(b: PsiBuilder, level: Int): Boolean {
        return b.structAllowed || b.tokenType != LBRACE
    }

    @JvmStatic fun withoutStructLiterals(b: PsiBuilder, level: Int, parser: Parser): Boolean =
        b.withContext(STRUCT_ALLOWED, false) { parser.parse(this, level) }

    @JvmStatic fun withStructLiterals(b: PsiBuilder, level: Int, parser: Parser): Boolean =
        b.withContext(STRUCT_ALLOWED, true) { parser.parse(this, level) }

    @JvmStatic fun withPathModeNoColons(b: PsiBuilder, level: Int, parser: Parser): Boolean =
        b.withContext(PATH_PARSING_MODE, PathParsingMode.NO_COLONS) { parser.parse(this, level) }

    @JvmStatic fun withPathModeColons(b: PsiBuilder, level: Int, parser: Parser): Boolean =
        b.withContext(PATH_PARSING_MODE, PathParsingMode.COLONS) { parser.parse(this, level) }

    @JvmStatic fun withPathModeNoTypes(b: PsiBuilder, level: Int, parser: Parser): Boolean =
        b.withContext(PATH_PARSING_MODE, PathParsingMode.NO_TYPES_ALLOWED) { parser.parse(this, level) }

    @JvmStatic fun isPathModeColons(b: PsiBuilder, level: Int): Boolean = b.pathParsingMode == PathParsingMode.COLONS
    @JvmStatic fun isPathModeNoColons(b: PsiBuilder, level: Int): Boolean = b.pathParsingMode == PathParsingMode.NO_COLONS

    @JvmStatic fun unpairedToken(b: PsiBuilder, level: Int): Boolean =
        when (b.tokenType) {
            LBRACE, RBRACE -> false
            LPAREN, RPAREN -> false
            LBRACK, RBRACK -> false
            else -> {
                b.advanceLexer()
                true
            }
        }

    @JvmStatic fun gtgteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, GTGTEQ, GT, GT, EQ)
    @JvmStatic fun gtgtImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, GTGT, GT, GT)
    @JvmStatic fun gteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, GTEQ, GT, EQ)
    @JvmStatic fun ltlteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, LTLTEQ, LT, LT, EQ)
    @JvmStatic fun ltltImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, LTLT, LT, LT)
    @JvmStatic fun lteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, LTEQ, LT, EQ)
    @JvmStatic fun ororImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, OROR, OR, OR)
    @JvmStatic fun andandImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, ANDAND, AND, AND)

    @JvmStatic fun defaultKeyword(b: PsiBuilder, level: Int): Boolean = contextualKeyword(b, "default", RustTokenElementTypes.DEFAULT)
    @JvmStatic fun unionKeyword(b: PsiBuilder, level: Int): Boolean = contextualKeyword(b, "union", RustTokenElementTypes.UNION)


    private @JvmStatic fun collapse(b: PsiBuilder, tokenType: IElementType, vararg parts: IElementType): Boolean {
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

    private val BLOCK_LIKE = TokenSet.create(
        RustCompositeElementTypes.WHILE_EXPR,
        RustCompositeElementTypes.WHILE_LET_EXPR,
        RustCompositeElementTypes.IF_EXPR,
        RustCompositeElementTypes.IF_LET_EXPR,
        RustCompositeElementTypes.FOR_EXPR,
        RustCompositeElementTypes.LOOP_EXPR,
        RustCompositeElementTypes.MATCH_EXPR,
        RustCompositeElementTypes.BLOCK_EXPR
    )

    @JvmStatic fun isBlockLike(b: PsiBuilder, level: Int): Boolean {
        val m = b.latestDoneMarker ?: return false
        return m.tokenType in BLOCK_LIKE || m.isBracedMacro(b)
    }

    private fun <T> PsiBuilder.withContext(key: Key<T>, value: T, block: PsiBuilder.() -> Boolean): Boolean {
        val old = getUserData(key)
        putUserData(key, value)
        val result = block()
        putUserData(key, old)
        return result
    }

    private fun LighterASTNode.isBracedMacro(b: PsiBuilder): Boolean =
        tokenType == RustCompositeElementTypes.MACRO_EXPR &&
            '{' == b.originalText.subSequence(startOffset, endOffset).find { it == '{' || it == '[' || it == '(' }

    private fun contextualKeyword(b: PsiBuilder, keyword: String, elementType: RustTokenType): Boolean {
        if (b.tokenType == RustTokenElementTypes.IDENTIFIER && b.tokenText == keyword) {
            b.remapCurrentToken(elementType)
            b.advanceLexer()
            return true
        }
        return false
    }

}

