/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.parser.RustParser
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.parser.clearFrame
import org.rust.lang.core.psi.RS_KEYWORDS
import org.rust.lang.core.psi.RS_LITERALS
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsElementTypes.QUOTE_IDENTIFIER
import org.rust.lang.core.psi.RsElementTypes.TT
import org.rust.lang.core.psi.tokenSetOf

enum class FragmentKind(private val kind: String) {
    Ident("ident"),
    Path("path"),
    Expr("expr"),
    Ty("ty"),
    Pat("pat"),
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
            Pat -> RustParser.Pat(builder, 0)
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

    private fun parseIdentifier(b: PsiBuilder): Boolean = b.consumeToken(IDENTIFIER_TOKENS)

    private fun parseStatement(b: PsiBuilder): Boolean =
        RustParser.LetDecl(b, 0) || RustParser.Expr(b, 0, -1)

    private fun parseVis(b: PsiBuilder): Boolean {
        RustParser.Vis(b, 0)
        return true // Vis can be empty
    }

    private fun parseTT(b: PsiBuilder): Boolean {
        return RustParserUtil.unpairedToken(b, 0) || RustParserUtil.parseTokenTreeLazy(b, 0, TT)
    }

    private fun parseLiteral(b: PsiBuilder): Boolean = b.consumeToken(RS_LITERALS)

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

        /**
         * Some tokens that treated as keywords by our lexer,
         * but rustc's macro parser treats them as identifiers
         */
        private val IDENTIFIER_TOKENS = TokenSet.orSet(tokenSetOf(RsElementTypes.IDENTIFIER), RS_KEYWORDS)
    }
}
