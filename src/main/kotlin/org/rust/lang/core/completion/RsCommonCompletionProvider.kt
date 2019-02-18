/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.rust.ide.inspections.import.AutoImportFix
import org.rust.ide.inspections.import.ImportContext
import org.rust.ide.inspections.import.importItem
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psiElement
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.stubs.index.RsReexportIndex
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.Testmark

object RsCommonCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        // Use original position if possible to re-use caches of the real file
        val position = CompletionUtil.getOriginalElement(parameters.position)
            ?.takeIf { isAncestorTypesEquals(it, parameters.position) }
            ?: parameters.position
        val element = position.parent as RsReferenceElement
        if (position !== element.referenceNameElement) return

        // This set will contain the names of all paths that have been added to the `result` by this provider.
        val processedPathNames = hashSetOf<String>()

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
                                filterPathCompletionVariantsByTraitBounds(
                                    addProcessedPathName(it, processedPathNames),
                                    lookup
                                ),
                                element.containingMod
                            )
                        )
                    )
                }

                is RsMethodCall -> {
                    val lookup = ImplLookup.relativeTo(element)
                    val receiver = CompletionUtil.getOriginalOrSelf(element.receiver).type
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
                    val receiver = CompletionUtil.getOriginalOrSelf(element.receiver).type
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

        addCompletionsFromIndex(parameters, result, processedPathNames)
    }

    private fun addCompletionsFromIndex(
        parameters: CompletionParameters,
        result: CompletionResultSet,
        processedPathNames: Set<String>
    ) {
        if (!simplePathPattern.accepts(parameters.position)) return
        // We use the position in the original file in order not to process empty paths
        val path = parameters.originalPosition?.parent as? RsPath ?: return
        if (TyPrimitive.fromPath(path) != null) return
        Testmarks.pathCompletionFromIndex.hit()

        val project = parameters.originalFile.project
        val importContext = ImportContext.from(project, path, true)

        val keys = hashSetOf<String>().apply {
            val explicitNames = StubIndex.getInstance().getAllKeys(RsNamedElementIndex.KEY, project)
            val reexportedNames = StubIndex.getInstance().getAllKeys(RsReexportIndex.KEY, project)

            addAll(explicitNames)
            addAll(reexportedNames)

            // Filters out path names that have already been added to `result`
            removeAll(processedPathNames)
        }

        for (elementName in CompletionUtil.sortMatching(result.prefixMatcher, keys)) {
            val candidates = AutoImportFix.getImportCandidates(importContext, elementName, elementName) {
                !(it.item is RsMod || it.item is RsModDeclItem || it.item.parent is RsMembers)
            }

            candidates
                .distinctBy { it.qualifiedNamedItem.item }
                .map { candidate ->
                    val item = candidate.qualifiedNamedItem.item
                    createLookupElement(item, elementName, candidate.info.usePath, object : RsDefaultInsertHandler() {
                        override fun handleInsert(element: RsElement, scopeName: String, context: InsertionContext, item: LookupElement) {
                            super.handleInsert(element, scopeName, context, item)
                            context.commitDocument()
                            val mod = PsiTreeUtil.findElementOfClassAtOffset(context.file, context.startOffset, RsMod::class.java, false) ?: return
                            mod.importItem(candidate)
                        }
                    })
                }
                .forEach(result::addElement)
        }
    }

    val elementPattern: ElementPattern<PsiElement>
        get() = PlatformPatterns.psiElement().withParent(psiElement<RsReferenceElement>())

    private val simplePathPattern: ElementPattern<PsiElement>
        get() {
            val simplePath = psiElement<RsPath>()
                .with(object : PatternCondition<RsPath>("SimplePath") {
                    override fun accepts(path: RsPath, context: ProcessingContext?): Boolean =
                        path.kind == PathKind.IDENTIFIER &&
                            path.path == null &&
                            path.typeQual == null &&
                            !path.hasColonColon &&
                            path.ancestorStrict<RsUseSpeck>() == null
                })
            return PlatformPatterns.psiElement().withParent(simplePath)
        }

    object Testmarks {
        val pathCompletionFromIndex = Testmark("pathCompletionFromIndex")
    }
}

private fun isAncestorTypesEquals(psi1: PsiElement, psi2: PsiElement): Boolean =
    psi1.ancestors.zip(psi2.ancestors).all { (a, b) -> a.javaClass == b.javaClass }

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

private fun addProcessedPathName(
    processor: RsResolveProcessor,
    processedPathNames: MutableSet<String>
): RsResolveProcessor = fun(it: ScopeEntry): Boolean {
    if (it.element != null) {
        processedPathNames.add(it.name)
    }
    return processor(it)
}
