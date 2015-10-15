package org.rust.lang.highlight

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.lexer.RustTokenElementTypes

public class RustBraceMatcher() : PairedBraceMatcher {

    public override fun getPairs() = PAIRS

    public override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean {
        return true
    }

    public override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int {
        return openingBraceOffset
    }

    companion object {
        val PAIRS: Array<BracePair> = arrayOf(
                BracePair(RustTokenElementTypes.LBRACE, RustTokenElementTypes.RBRACE, true /* structural */),
                BracePair(RustTokenElementTypes.LPAREN, RustTokenElementTypes.RPAREN, false),
                BracePair(RustTokenElementTypes.RBRACK, RustTokenElementTypes.RBRACK, false)
        )
    }
}

