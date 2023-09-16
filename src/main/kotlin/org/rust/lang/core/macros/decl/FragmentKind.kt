/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.decl

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.parser.RustParser
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.parser.clearFrame
import org.rust.lang.core.parser.rollbackIfFalse
import org.rust.lang.core.psi.RS_IDENTIFIER_TOKENS
import org.rust.lang.core.psi.RsElementTypes.*

enum class FragmentKind(private val kind: String) {
    Ident("ident"),
    Path("path"),
    Expr("expr"),
    Ty("ty"),
    Pat("pat"),
    PatParam("pat_param"),
    Stmt("stmt"),
    Block("block"),
    Item("item"),
    Meta("meta"),
    Tt("tt"),
    Vis("vis"),
    Literal("literal"),
    Lifetime("lifetime");

    fun parse(builder: PsiBuilder): Boolean {
        builder.clearFrame()
        return when (this) {
            Ident -> parseIdentifier(builder)
            Path -> RustParser.TypePathGenericArgsNoTypeQual(builder, 0)
            Expr -> RustParser.Expr(builder, 0, -1)
            Ty -> RustParser.TypeReference(builder, 0)
            Pat -> RustParserUtil.parseSimplePat(builder) // TODO `RustParser.Pat(builder, 0)` on 2021 edition
            PatParam -> RustParserUtil.parseSimplePat(builder)
            Stmt -> parseStatement(builder)
            Block -> RustParserUtil.parseCodeBlockLazy(builder, 0)
            Item -> RustParser.Item(builder, 0)
            Meta -> RustParser.MetaItemWithoutTT(builder, 0)
            Vis -> parseVis(builder)
            Tt -> parseTT(builder)
            Lifetime -> GeneratedParserUtilBase.consumeTokenFast(builder, QUOTE_IDENTIFIER)
            Literal -> parseLiteral(builder)
        }
    }

    private fun parseIdentifier(b: PsiBuilder): Boolean = b.consumeToken(RS_IDENTIFIER_TOKENS)

    private fun parseStatement(b: PsiBuilder): Boolean =
        RustParser.LetDecl(b, 0) || RustParser.Expr(b, 0, -1)

    private fun parseVis(b: PsiBuilder): Boolean {
        RustParser.Vis(b, 0)
        return true // Vis can be empty
    }

    private fun parseTT(b: PsiBuilder): Boolean {
        return RustParserUtil.unpairedToken(b, 0) || RustParserUtil.parseTokenTreeLazy(b, 0, TT)
    }

    private fun parseLiteral(b: PsiBuilder): Boolean {
        return b.rollbackIfFalse {
            val hasMinus = GeneratedParserUtilBase.consumeTokenFast(b, MINUS)
            if (!hasMinus || b.tokenType == INTEGER_LITERAL || b.tokenType == FLOAT_LITERAL) {
                RustParser.LitExprWithoutAttrs(b, 0)
            } else {
                false
            }
        }
    }

    private fun PsiBuilder.consumeToken(tokenSet: TokenSet): Boolean {
        return if (tokenType in tokenSet) {
            advanceLexer()
            true
        } else {
            false
        }
    }

    companion object {
        private val fragmentKinds: Map<String, FragmentKind> = values().associateBy { it.kind }

        val kinds: Set<String> = fragmentKinds.keys

        fun fromString(s: String): FragmentKind? = fragmentKinds[s]
    }
}
