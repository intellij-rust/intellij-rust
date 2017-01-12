package org.rust.ide.spelling

import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RustTokenElementTypes.STRING_LITERAL

class RsSpellcheckingStrategy : SpellcheckingStrategy() {

    override fun isMyContext(element: PsiElement) = RustLanguage.`is`(element.language)

    override fun getTokenizer(element: PsiElement?): Tokenizer<*> =
        if (element?.node?.elementType == STRING_LITERAL)
            StringLiteralTokenizer
        else
            super.getTokenizer(element)
}

