package org.rust.ide.typing

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustTokenElementTypes

class RustBraceMatcher() : PairedBraceMatcher {

    override fun getPairs() = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean {
        return true
    }

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int {
        return openingBraceOffset
    }

    companion object {
        val PAIRS: Array<BracePair> = arrayOf(
            BracePair(RustTokenElementTypes.LBRACE, RustTokenElementTypes.RBRACE, true /* structural */),
            BracePair(RustTokenElementTypes.LPAREN, RustTokenElementTypes.RPAREN, false),
            BracePair(RustTokenElementTypes.LBRACK, RustTokenElementTypes.RBRACK, false)
        )
    }
}

