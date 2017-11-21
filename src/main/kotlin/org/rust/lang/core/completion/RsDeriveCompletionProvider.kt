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
import org.rust.ide.icons.RsIcons
import org.rust.ide.icons.multiple
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.isStdDerivable
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.StdDerivableTrait
import org.rust.lang.core.resolve.withDependencies

object RsDeriveCompletionProvider : CompletionProvider<CompletionParameters>() {

    private const val DEFAULT_PRIORITY = 5.0
    private const val GROUP_PRIORITY = DEFAULT_PRIORITY + 0.1

    override fun addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext?,
                                result: CompletionResultSet) {

        val owner = parameters.position.ancestorStrict<RsStructOrEnumItemElement>()
            ?: return
        val alreadyDerived = ImplLookup.relativeTo(owner)
            .findImplsAndTraits(owner.declaredType)
            .mapNotNull {
                val (trait, _) = it.implementedTrait ?: return@mapNotNull null
                if (trait.isStdDerivable) trait.name else null
            }

        StdDerivableTrait.values()
            .filter { it.name !in alreadyDerived }
            .forEach { trait ->
                val traitWithDependencies = trait.withDependencies
                    .filter { it.name !in alreadyDerived }
                // if 'traitWithDependencies' contains only one element
                // then all trait dependencies are already satisfied
                // and 'traitWithDependencies' contains only 'trait' element
                if (traitWithDependencies.size > 1) {
                    val element = LookupElementBuilder.create(traitWithDependencies.joinToString(", "))
                        .withIcon(RsIcons.TRAIT.multiple())
                    result.addElement(element.withPriority(GROUP_PRIORITY))
                }
                val element = LookupElementBuilder.create(trait.name)
                    .withIcon(RsIcons.TRAIT)
                result.addElement(element.withPriority(DEFAULT_PRIORITY))
            }
    }

    val elementPattern: ElementPattern<PsiElement>
        get() {

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
