/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.hasCself
import org.rust.lang.core.psi.ext.receiver
import org.rust.lang.core.psiElement
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

object RsCommonCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position.parent as RsReferenceElement
        if (parameters.position !== element.referenceNameElement) return
        collectCompletionVariants(result) {
            when (element) {
                is RsAssocTypeBinding -> processAssocTypeVariants(element, it)
                is RsExternCrateItem -> processExternCrateResolveVariants(element, true, it)
                is RsLabel -> processLabelResolveVariants(element, it)
                is RsLifetime -> processLifetimeResolveVariants(element, it)
                is RsMacroReference -> processMacroReferenceVariants(element, it)
                is RsModDeclItem -> processModDeclResolveVariants(element, it)
                is RsPatBinding -> processPatBindingResolveVariants(element, true, it)
                is RsStructLiteralField -> processStructLiteralFieldResolveVariants(element, true, it)

                is RsPath -> {
                    val lookup = ImplLookup.relativeTo(element)
                    processPathResolveVariants(
                        lookup,
                        element,
                        true,
                        filterAssocTypes(
                            element,
                            filterCompletionVariantsByVisibility(
                                filterPathCompletionVariantsByTraitBounds(it, lookup),
                                element.containingMod
                            )
                        )
                    )
                }

                is RsMethodCall -> {
                    val lookup = ImplLookup.relativeTo(element)
                    val receiver = element.receiver.type
                    processMethodCallExprResolveVariants(
                        lookup,
                        receiver,
                        filterCompletionVariantsByVisibility(
                            filterMethodCompletionVariantsByTraitBounds(it, lookup, receiver),
                            element.containingMod
                        )
                    )
                }

                is RsFieldLookup -> {
                    val lookup = ImplLookup.relativeTo(element)
                    val receiver = element.receiver.type
                    processDotExprResolveVariants(
                        lookup,
                        receiver,
                        filterCompletionVariantsByVisibility(
                            filterMethodCompletionVariantsByTraitBounds(it, lookup, receiver),
                            element.containingMod
                        )
                    )
                }
            }
        }
    }

    val elementPattern: ElementPattern<PsiElement>
        get() = PlatformPatterns.psiElement()
            .withParent(psiElement<RsReferenceElement>())
            .withLanguage(RsLanguage)
}

private fun filterAssocTypes(
    path: RsPath,
    processor: RsResolveProcessor
): RsResolveProcessor {
    val qualifier = path.path
    val allAssocItemsAllowed =
        qualifier == null || qualifier.hasCself || qualifier.reference.resolve() is RsTypeParameter
    return if (allAssocItemsAllowed) processor else fun(it: ScopeEntry): Boolean {
        if (it is AssocItemScopeEntry && (it.element is RsTypeAlias)) return false
        return processor(it)
    }
}

private fun filterPathCompletionVariantsByTraitBounds(
    processor: RsResolveProcessor,
    lookup: ImplLookup
): RsResolveProcessor {
    val cache = hashMapOf<RsImplItem, Boolean>()
    return fun(it: ScopeEntry): Boolean {
        if (it !is AssocItemScopeEntry) return processor(it)
        if (it.source !is TraitImplSource.ExplicitImpl) return processor(it)

        val receiver = it.subst[TyTypeParameter.self()] ?: return processor(it)
        // Don't filter partially unknown types
        if (receiver.containsTyOfClass(TyUnknown::class.java)) return processor(it)
        // Filter members by trait bounds (try to select all obligations for each impl)
        // We're caching evaluation results here because we can often complete members
        // in the same impl and always have the same receiver type
        val canEvaluate = cache.getOrPut(it.source.value) {
            lookup.ctx.canEvaluateBounds(it.source.value, receiver)
        }
        if (canEvaluate) return processor(it)

        return false
    }
}

private fun filterMethodCompletionVariantsByTraitBounds(
    processor: RsResolveProcessor,
    lookup: ImplLookup,
    receiver: Ty
): RsResolveProcessor {
    // Don't filter partially unknown types
    if (receiver.containsTyOfClass(TyUnknown::class.java)) return processor

    val cache = mutableMapOf<RsImplItem, Boolean>()
    return fun(it: ScopeEntry): Boolean {
        // If not a method (actually a field) or a trait method - just process it
        if (it !is MethodResolveVariant || it.source !is TraitImplSource.ExplicitImpl) return processor(it)
        // Filter methods by trait bounds (try to select all obligations for each impl)
        // We're caching evaluation results here because we can often complete methods
        // in the same impl and always have the same receiver type
        val canEvaluate = cache.getOrPut(it.source.value) {
            lookup.ctx.canEvaluateBounds(it.source.value, receiver)
        }
        if (canEvaluate) return processor(it)

        return false
    }
}
