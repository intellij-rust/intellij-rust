/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.MultiMap
import org.rust.ide.icons.RsIcons
import org.rust.ide.utils.import.ImportCandidate
import org.rust.ide.utils.import.ImportCandidatesCollector
import org.rust.ide.utils.import.ImportContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.completion.RsLookupElementProperties.ElementKind
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psiElement
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.with

object RsDeriveCompletionProvider : RsCompletionProvider() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position.safeGetOriginalOrSelf()
        addCompletionsForStdlibBuiltinDerives(position, result)

        val path = position.parent as? RsPath ?: return
        val processedElements = MultiMap<String, RsElement>()
        addCompletionsForInScopeDerives(path, result, processedElements)
        addCompletionsForOutOfScopeDerives(path, result, processedElements)
    }

    /** Derives in stdlib don't have corresponding proc macros, so we have to hardcode them */
    private fun addCompletionsForStdlibBuiltinDerives(position: PsiElement, result: CompletionResultSet) {
        val owner = position.ancestorStrict<RsStructOrEnumItemElement>() ?: return
        val ownerType = owner.declaredType
        val lookup = ImplLookup.relativeTo(owner)

        /** Filter unavailable and already derived */
        fun Sequence<KnownDerivableTrait>.filterDerivables() = filter {
            val trait = it.findTrait(owner.knownItems)
            trait != null && !lookup.canSelect(TraitRef(ownerType, trait.withDefaultSubst()))
        }

        KnownDerivableTrait.values().asSequence()
            .filter { it.isStd }
            .filterDerivables()
            .forEach { derivable ->
                val traitWithDependencies = derivable.withDependencies.asSequence()
                    .filterDerivables()
                    .toList()
                // if 'traitWithDependencies' contains only one element
                // then all derivable dependencies are already satisfied
                // and 'traitWithDependencies' contains only 'derivable' element
                if (traitWithDependencies.size > 1) {
                    val element = LookupElementBuilder.create(traitWithDependencies.joinToString(", "))
                        .withIcon(RsIcons.MACRO)
                    result.addElement(element.toRsLookupElement(RsLookupElementProperties(elementKind = ElementKind.DERIVE_GROUP)))
                }
                result.addElement(createLookupElement(derivable.name))
            }
    }

    private fun addCompletionsForInScopeDerives(path: RsPath, result: CompletionResultSet, processedElements: MultiMap<String, RsElement>) {
        val processor = createProcessor { e ->
            result.addElement(createLookupElement(e.name))
            processedElements.putValue(e.name, e.element)
            false
        }
        val filtered = filterDeriveProcMacros(processor)
        processProcMacroResolveVariants(path, filtered, isCompletion = true)
    }

    private fun addCompletionsForOutOfScopeDerives(path: RsPath, result: CompletionResultSet, processedElements: MultiMap<String, RsElement>) {
        val importContext = ImportContext.from(path, ImportContext.Type.COMPLETION) ?: return
        val candidates = ImportCandidatesCollector.getCompletionCandidates(importContext, result.prefixMatcher, processedElements)
        for (candidate in candidates) {
            val item = candidate.item
            if (item !is RsFunction || !item.isCustomDeriveProcMacroDef) continue
            val name = item.procMacroName ?: continue
            result.addElement(createLookupElement(name, candidate))
        }
    }

    private fun createLookupElement(name: String, candidate: ImportCandidate? = null): LookupElement {
        val builder = LookupElementBuilder.create(name).withIcon(RsIcons.MACRO)
        val properties = RsLookupElementProperties(elementKind = ElementKind.DERIVE)
        return if (candidate != null) {
            builder
                .withInsertHandler { context, _ -> context.import(candidate) }
                .appendTailText(" (${candidate.info.usePath})", true)
                .toRsLookupElement(properties)
                .withImportCandidate(candidate)
        } else {
            builder.toRsLookupElement(properties)
        }
    }

    override val elementPattern: ElementPattern<out PsiElement>
        get() {
            return PlatformPatterns.psiElement()
                .withLanguage(RsLanguage)
                // avoid completion in non primitive path like `derive[std::/*caret*/]`
                .withParent(psiElement<RsPath>()
                    .with("PrimitivePath") { path -> path.path == null }
                    .withParent(RsPsiPattern.derivedTraitMetaItem)
                )
        }
}
