package org.rust.lang.core.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderUtil
import com.intellij.lang.WhitespacesBinders
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.util.Key
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.lexer.containsEOL
import org.rust.lang.core.psi.RustCompositeElementTypes
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.utils.Cookie
import org.rust.lang.utils.using

object RustParserUtil : GeneratedParserUtilBase() {
    private val STRUCT_ALLOWED: Key<Boolean> = Key("org.rust.STRUCT_ALLOWED")

    private fun PsiBuilder.getStructAllowed(): Boolean {
        return getUserData(STRUCT_ALLOWED) ?: true
    }

    private fun PsiBuilder.setStructAllowed(value: Boolean): Boolean {
        val r = getStructAllowed()
        putUserData(STRUCT_ALLOWED, value)
        return r
    }

    private val docCommentBinder = WhitespacesBinders.leadingCommentsBinder(
        TokenSet.create(RustTokenElementTypes.OUTER_DOC_COMMENT)
    )


    //
    // Helpers
    //

    @JvmStatic fun injectInto(b: PsiBuilder, level: Int, s: Parser, t: Parser): Boolean {
        fun done(m: PsiBuilder.Marker, tt: IElementType) {
            m.done(tt)
        }

        fun drop(m: PsiBuilder.Marker) {
            m.drop()
        }

        val m = b.mark()
        var r: Boolean

        try {

            r = s.parse(b, level)
            r = r && t.parse(b, level)

        } catch (t: Throwable) {
            drop(m)
            throw t
        }

        if (r) {
            // This is the one that should be done
            // by `t`
            b.latestDoneMarker?.let { p ->
                done(m, p.tokenType)
                drop(p as PsiBuilder.Marker)
            }
            return true
        }

        drop(m)
        return false;
    }

    @JvmStatic fun bindDocComments(b: PsiBuilder, @Suppress("UNUSED_PARAMETER") level: Int): Boolean {
        (b.latestDoneMarker as PsiBuilder.Marker).setCustomEdgeTokenBinders(docCommentBinder, null)
        return true
    }

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

    @JvmStatic fun checkStructAllowed(b: PsiBuilder, @Suppress("UNUSED_PARAMETER") level: Int): Boolean = b.getStructAllowed()

    @JvmStatic fun checkBraceAllowed(b: PsiBuilder, @Suppress("UNUSED_PARAMETER") level: Int): Boolean {
        return b.getStructAllowed() || b.tokenType != LBRACE
    }

    @JvmStatic fun withoutStructLiterals(b: PsiBuilder, level: Int, parser: GeneratedParserUtilBase.Parser): Boolean {
        return using (Cookie(b, { this.setStructAllowed(it) }, false)) {
            parser.parse(b, level)
        }
    }

    @JvmStatic fun withStructLiterals(b: PsiBuilder, level: Int, parser: GeneratedParserUtilBase.Parser): Boolean {
        return using (Cookie(b, { this.setStructAllowed(it) }, true)) {
            parser.parse(b, level)
        }
    }

    @JvmStatic fun skipUntilEOL(b: PsiBuilder, @Suppress("UNUSED_PARAMETER") level: Int): Boolean {
        while (!b.eof()) {
            if (b.tokenType == TokenType.WHITE_SPACE
                || b.tokenText?.containsEOL() != null) return true;

            b.advanceLexer();
        }

        return false;
    }

    @JvmStatic fun unpairedToken(b: PsiBuilder, @Suppress("UNUSED_PARAMETER") level: Int): Boolean =
        when (b.tokenType) {
            LBRACE, RBRACE -> false
            LPAREN, RPAREN -> false
            LBRACK, RBRACK -> false
            else           -> {
                b.advanceLexer();
                true
            }
        }

    @JvmStatic fun collapse(b: PsiBuilder, @Suppress("UNUSED_PARAMETER") level: Int,
                            tokenType: IElementType, vararg parts: IElementType): Boolean {
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
}

