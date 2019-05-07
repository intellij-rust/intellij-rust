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
import org.rust.ide.inspections.import.insertExternCrateItem
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.KnownDerivableTrait
import org.rust.lang.core.resolve.indexes.RsDeriveIndex
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.resolve.withDependencies
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.ty.Ty

object RsDeriveCompletionProvider : CompletionProvider<CompletionParameters>() {

    private const val DEFAULT_PRIORITY = 5.0
    private const val GROUP_PRIORITY = DEFAULT_PRIORITY + 0.1

    override fun addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                result: CompletionResultSet) {

        val owner = parameters.position.ancestorStrict<RsStructOrEnumItemElement>()
            ?: return
        val ownerType = owner.declaredType
        val lookup = ImplLookup.relativeTo(owner)

        KnownDerivableTrait.values()
            .asSequence()
            .filter { it.findTrait(owner.knownItems)?.isImplementedFor(ownerType, lookup) == false }
            .forEach { derivable ->
                val traitWithDependencies = derivable.withDependencies.asSequence()
                    .filter { it.findTrait(owner.knownItems)?.isImplementedFor(ownerType, lookup) == false }
                    .toList()
                // if 'traitWithDependencies' contains only one element
                // then all derivable dependencies are already satisfied
                // and 'traitWithDependencies' contains only 'derivable' element
                if (traitWithDependencies.size > 1) {
                    val element = LookupElementBuilder.create(traitWithDependencies.joinToString(", "))
                        .withIcon(RsIcons.TRAIT.multiple())
                    result.addElement(element.withPriority(GROUP_PRIORITY))
                }
                val element = LookupElementBuilder.create(derivable.name)
                    .withIcon(RsIcons.TRAIT)
                result.addElement(element.withPriority(DEFAULT_PRIORITY))
            }
        RsDeriveIndex.allDeriverableTraits(owner.project)
            .associate {
                // Find trait by it's name
                RsNamedElementIndex.findElementsByName(owner.project, it.name, it.deriver.resolveScope)
                    .filterIsInstance<RsTraitItem>().firstOrNull() to it.deriver
            }
            .filterKeys {
                // Past this point, trait can't be null (but the inference doesn't understand that)
                it?.isImplementedFor(ownerType, lookup) == false
            }
            .forEach { (trait, deriver) ->
                val mainFile = owner.crateRoot
                val deriverCrateName = deriver.containingCargoPackage?.name

                val name = trait!!.name ?: return@forEach
                val element = LookupElementBuilder.create(name)
                    .withIcon(RsIcons.TRAIT)
                    .withInsertHandler { context, item ->
                        // If #[macro_use] extern crate <deriver's crate> is not present, add it
                        if (mainFile != null) {
                            val externCrates = mainFile.childrenOfType<RsExternCrateItem>()
                            val externCrate = externCrates.find {
                                it.identifier?.text == deriverCrateName
                            } ?: deriverCrateName?.let {
                                mainFile.insertExternCrateItem(RsPsiFactory(context.project), it)
                            }

                            if (externCrate?.hasOuterAttr("macro_use") == false) {
                                externCrate.addOuterAttribute(Attribute("macro_use"))
                            }
                            context.commitDocument()
                            // FIXME: for some reason even though this is invoked, it actually doesnt do anything
                        }
                    }
                result.addElement(element.withPriority(DEFAULT_PRIORITY))
            }
    }

    private fun RsTraitItem.isImplementedFor(owner: Ty, lookup: ImplLookup): Boolean =
        lookup.canSelect(TraitRef(owner, this.withDefaultSubst()))

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
