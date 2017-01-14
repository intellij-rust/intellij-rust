package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes.COLON
import org.rust.lang.core.psi.util.elementType

class RsCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, DeriveCompletionProvider.elementPattern, DeriveCompletionProvider)
        extend(CompletionType.BASIC, AttributeCompletionProvider.elementPattern, AttributeCompletionProvider)
    }

    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean =
        typeChar == ':' && position.elementType == COLON
}
