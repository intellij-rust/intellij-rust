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

object RsDeriveCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val DERIVABLE_TRAITS = listOf(
        "Eq", "PartialEq",
        "Ord", "PartialOrd",
        "Copy", "Clone",
        "Hash",
        "Default",
        "Debug",
        "RustcEncodable", "RustcDecodable"
    )

    override fun addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext?,
                                result: CompletionResultSet) {

        val outerAttrElem = parameters.position.parentOfType<RsOuterAttr>()
            ?: return
        val alreadyDerived = outerAttrElem.metaItem.metaItemArgs?.metaItemList.orEmpty()
            .mapNotNull { it.identifier.text }
        val lookupElements = DERIVABLE_TRAITS.filter { it !in alreadyDerived }
            .map { LookupElementBuilder.create(it) }
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
