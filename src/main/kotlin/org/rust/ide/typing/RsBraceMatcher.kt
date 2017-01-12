package org.rust.ide.typing

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.RustTokenElementTypes.*

class RsBraceMatcher() : PairedBraceMatcher {

    override fun getPairs() = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, next: IElementType?): Boolean =
        next in InsertPairBraceBefore

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS: Array<BracePair> = arrayOf(
            BracePair(RustTokenElementTypes.LBRACE, RustTokenElementTypes.RBRACE, true /* structural */),
            BracePair(RustTokenElementTypes.LPAREN, RustTokenElementTypes.RPAREN, false),
            BracePair(RustTokenElementTypes.LBRACK, RustTokenElementTypes.RBRACK, false)
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
