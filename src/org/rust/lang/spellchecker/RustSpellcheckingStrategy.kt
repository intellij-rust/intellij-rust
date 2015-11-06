package org.rust.lang.spellchecker

import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RustLitExpr

class RustSpellcheckingStrategy : SpellcheckingStrategy() {
    private val literalExpressionTokenizer = LiteralExpressionTokenizer()

    override fun isMyContext(element: PsiElement) = RustLanguage.INSTANCE.`is`(element.language)

    override fun getTokenizer(element: PsiElement?): Tokenizer<*> {
        if (element is RustLitExpr) {
            return literalExpressionTokenizer
        }
        return super.getTokenizer(element)
    }
}

