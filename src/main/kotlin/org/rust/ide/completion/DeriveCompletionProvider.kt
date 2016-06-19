package org.rust.ide.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RustLanguage
import org.rust.lang.core.parser.RustPsiTreeUtil
import org.rust.lang.core.psi.RustCompositeElementTypes
import org.rust.lang.core.psi.RustMetaItemElement
import org.rust.lang.core.psi.RustOuterAttrElement
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.util.parentOfType

object DeriveCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val DERIVABLE_TRAITS = listOf("Eq", "PartialEq", "Ord", "PartialOrd", "Copy", "Clone", "Hash", "Default",
        "Debug")

    override fun addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext?,
                                result: CompletionResultSet) {

        val outerAttrElem = parameters.position.parentOfType<RustOuterAttrElement>()
        val deriveMetaItem = RustPsiTreeUtil.getChildOfType(outerAttrElem, RustMetaItemElement::class.java)
        val alreadyDerived = RustPsiTreeUtil.getChildrenOfType(deriveMetaItem, RustMetaItemElement::class.java)
            ?.mapNotNull { it.firstChild.text }.orEmpty()
        val lookupElements = DERIVABLE_TRAITS.filter { it !in alreadyDerived }
            .map { LookupElementBuilder.create(it) }
        result.addAllElements(lookupElements)
    }

    val elementPattern: ElementPattern<PsiElement> get() {

        val deriveIdentifier = psiElement(RustTokenElementTypes.IDENTIFIER)
            .withText("derive")

        val traitEntry = psiElement(RustCompositeElementTypes.META_ITEM)
            .afterLeafSkipping(psiElement(RustTokenElementTypes.LPAREN), deriveIdentifier)

        val deriveMetaItem = psiElement(RustCompositeElementTypes.META_ITEM)
            .withFirstChild(traitEntry)
            .withParent(psiElement(RustCompositeElementTypes.OUTER_ATTR))

        val traitMetaItem = psiElement(RustCompositeElementTypes.META_ITEM)
            .withParent(deriveMetaItem)

        return psiElement().inside(traitMetaItem).withLanguage(RustLanguage);
    }
}
