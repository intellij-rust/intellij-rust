package org.toml.ide

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.toml.lang.core.psi.TomlTypes

public class TomlBraceMatcher() : PairedBraceMatcher {

    public override fun getPairs() = PAIRS

    public override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean {
        return true
    }

    public override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int {
        return openingBraceOffset
    }

    companion object {
        val PAIRS: Array<BracePair> = arrayOf(
            BracePair(TomlTypes.LBRACE, TomlTypes.RBRACE, false),
            BracePair(TomlTypes.LBRACKET, TomlTypes.RBRACKET, false)
        )
    }
}

