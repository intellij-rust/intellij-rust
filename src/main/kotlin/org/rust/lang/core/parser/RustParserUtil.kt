package org.rust.lang.core.parser

import com.intellij.lang.*
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.RS_BLOCK_LIKE_EXPRESSIONS
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsElementTypes.*

@Suppress("UNUSED_PARAMETER")
object RustParserUtil : GeneratedParserUtilBase() {
    enum class PathParsingMode { COLONS, NO_COLONS, NO_TYPES }
    enum class BinaryMode { ON, OFF }

    private val STRUCT_ALLOWED: Key<Boolean> = Key("org.rust.STRUCT_ALLOWED")
    private val PATH_PARSING_MODE: Key<PathParsingMode> = Key("org.rust.PATH_PARSING_MODE")
    private val STMT_EXPR_MODE: Key<Boolean> = Key("org.rust.STMT_EXPR_MODE")

    private val PsiBuilder.structAllowed: Boolean get() = getUserData(STRUCT_ALLOWED) ?: true

    private val PsiBuilder.pathParsingMode: PathParsingMode get() = requireNotNull(getUserData(PATH_PARSING_MODE)) {
        "Path context is not set. Be sure to call one of `withParsingMode...` functions"
    }

    @JvmField val DOC_COMMENT_BINDER: WhitespacesAndCommentsBinder = WhitespacesBinders.leadingCommentsBinder(
        TokenSet.create(OUTER_BLOCK_DOC_COMMENT, OUTER_EOL_DOC_COMMENT))

    //
    // Helpers
    //

    @JvmStatic fun checkStructAllowed(b: PsiBuilder, level: Int): Boolean = b.structAllowed

    @JvmStatic fun checkBraceAllowed(b: PsiBuilder, level: Int): Boolean {
        return b.structAllowed || b.tokenType != LBRACE
    }

    @JvmStatic fun structLiterals(b: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        b.withContext(STRUCT_ALLOWED, mode == BinaryMode.ON) { parser.parse(this, level) }

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
    @JvmStatic fun stmtMode(b: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        b.withContext(STMT_EXPR_MODE, mode == BinaryMode.ON) { parser.parse(this, level) }

    @JvmStatic fun isCompleteBlockExpr(b: PsiBuilder, level: Int): Boolean {
        return isBlock(b, level) && b.getUserData(STMT_EXPR_MODE) == true
    }

    @JvmStatic fun isBlock(b: PsiBuilder, level: Int): Boolean {
        val m = b.latestDoneMarker ?: return false
        return m.tokenType in RS_BLOCK_LIKE_EXPRESSIONS || m.isBracedMacro(b)
    }

    @JvmStatic fun pathMode(b: PsiBuilder, level: Int, mode: PathParsingMode, parser: Parser): Boolean =
        b.withContext(PATH_PARSING_MODE, mode) { parser.parse(this, level) }

    @JvmStatic fun isPathMode(b: PsiBuilder, level: Int, mode: PathParsingMode): Boolean = mode == b.pathParsingMode

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

    @JvmStatic fun macroIdentifier(b: PsiBuilder, level: Int): Boolean =
        when (b.tokenType) {
            TYPE_KW,
            IDENTIFIER -> {
                b.advanceLexer()
                true
            }
            else -> false
        }

    @JvmStatic fun placeholderMacroToken(b: PsiBuilder, level: Int): Boolean =
        when (b.tokenType) {
            LBRACE, RBRACE -> false
            LPAREN, RPAREN -> false
            LBRACK, RBRACK -> false
            DOLLAR -> false
            else -> {
                b.advanceLexer()
                true
            }
        }

    @JvmStatic fun tokenAfterComplexPattern(b: PsiBuilder, level: Int): Boolean =
        when (b.tokenType) {
            LBRACE, RBRACE -> false
            LPAREN, RPAREN -> false
            LBRACK, RBRACK -> false
            DOLLAR -> false
            PLUS -> false
            MUL -> false
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

    @JvmStatic fun defaultKeyword(b: PsiBuilder, level: Int): Boolean = contextualKeyword(b, "default", DEFAULT)
    @JvmStatic fun unionKeyword(b: PsiBuilder, level: Int): Boolean = contextualKeyword(b, "union", UNION)


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

    private fun <T> PsiBuilder.withContext(key: Key<T>, value: T, block: PsiBuilder.() -> Boolean): Boolean {
        val old = getUserData(key)
        putUserData(key, value)
        val result = block()
        putUserData(key, old)
        return result
    }

    private fun LighterASTNode.isBracedMacro(b: PsiBuilder): Boolean =
        tokenType == RsElementTypes.MACRO_EXPR &&
            '{' == b.originalText.subSequence(startOffset, endOffset).find { it == '{' || it == '[' || it == '(' }

    private fun contextualKeyword(b: PsiBuilder, keyword: String, elementType: IElementType): Boolean {
        // Tricky: the token can be already remapped by some previous rule that was backtracked
        if ((b.tokenType == IDENTIFIER && b.tokenText == keyword && !(b.lookAhead(1)?.equals(EXCL) ?: false)) || b.tokenType == elementType) {
            b.remapCurrentToken(elementType)
            b.advanceLexer()
            return true
        }
        return false
    }

}

