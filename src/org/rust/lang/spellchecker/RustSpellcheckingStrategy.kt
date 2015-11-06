package org.rust.lang.spellchecker

import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import org.rust.lang.RustLanguage

class RustSpellcheckingStrategy : SpellcheckingStrategy() {
    override fun isMyContext(element: PsiElement) = RustLanguage.INSTANCE.`is`(element.language)
}
