package org.rust.ide.typing

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.psi.RsTokenElementTypes
import org.rust.lang.core.psi.RsTokenElementTypes.*

class RsBraceMatcher : PairedBraceMatcher {

    override fun getPairs() = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, next: IElementType?): Boolean =
        next in InsertPairBraceBefore

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS: Array<BracePair> = arrayOf(
            BracePair(RsTokenElementTypes.LBRACE, RsTokenElementTypes.RBRACE, true /* structural */),
            BracePair(RsTokenElementTypes.LPAREN, RsTokenElementTypes.RPAREN, false),
            BracePair(RsTokenElementTypes.LBRACK, RsTokenElementTypes.RBRACK, false),
            BracePair(RsTokenElementTypes.LT, RsTokenElementTypes.GT, false)
        )

        private val InsertPairBraceBefore = TokenSet.orSet(
            COMMENTS_TOKEN_SET,
            TokenSet.create(
                TokenType.WHITE_SPACE,
                SEMICOLON,
                COMMA,
                RPAREN,
                RBRACE,
                RBRACE, LBRACE
            )
        )
    }
}
