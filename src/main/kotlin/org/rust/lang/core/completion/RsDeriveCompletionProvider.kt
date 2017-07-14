/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsOuterAttr
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.resolve.StdDerivableTrait
import org.rust.lang.core.resolve.withDependencies

object RsDeriveCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext?,
                                result: CompletionResultSet) {

        val outerAttrElem = parameters.position.parentOfType<RsOuterAttr>()
            ?: return
        val alreadyDerived = outerAttrElem.metaItem.metaItemArgs?.metaItemList.orEmpty()
            .mapNotNull { it.identifier.text }

        val lookupElements = StdDerivableTrait.values()
            .filter { it.name !in alreadyDerived }
            .flatMap { trait ->
                val traitWithDependencies = trait.withDependencies
                    .filter { it.name !in alreadyDerived }
                // if 'traitWithDependencies' contains only one element
                // then all trait dependencies are already satisfied
                // and 'traitWithDependencies' contains only 'trait' element
                val variants = if (traitWithDependencies.size > 1) {
                    listOf(traitWithDependencies.joinToString(", "), trait.name)
                } else {
                    listOf(trait.name)
                }
                variants.map {LookupElementBuilder.create(it) }
            }
        result.addAllElements(lookupElements)
    }

    val elementPattern: ElementPattern<PsiElement> get() {

        val deriveAttr = psiElement(META_ITEM)
            .withParent(psiElement(OUTER_ATTR))
            .with(object : PatternCondition<PsiElement>("derive") {
                // `withFirstChild` does not handle leaf elements.
                // See a note in [com.intellij.psi.PsiElement.getChildren]
                override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean =
                    t.firstChild.text == "derive"
            })

        val traitMetaItem = psiElement(META_ITEM)
            .withParent(
                psiElement(META_ITEM_ARGS)
                    .withParent(deriveAttr)
            )

        return psiElement()
            .inside(traitMetaItem)
            .withLanguage(RsLanguage)
    }
}
