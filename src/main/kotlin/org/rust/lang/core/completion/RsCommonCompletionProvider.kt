/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapiext.Testmark
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.ide.utils.import.ImportCandidate
import org.rust.ide.utils.import.ImportCandidatesCollector
import org.rust.ide.utils.import.ImportContext
import org.rust.ide.utils.import.import
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.macros.findElementExpandedFrom
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psiElement
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.FieldResolveVariant
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.stubs.index.ReexportKey
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.stubs.index.RsReexportIndex
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

object RsCommonCompletionProvider : RsCompletionProvider() {
    override fun addCompletions(
        parameters: CompletionParameters,
        processingContext: ProcessingContext,
        result: CompletionResultSet
    ) {
        // Use original position if possible to re-use caches of the real file
        val position = parameters.position.safeGetOriginalOrSelf()
        val element = position.parent as RsReferenceElement
        if (position !== element.referenceNameElement) return

        // This set will contain the names of all paths that have been added to the `result` by this provider.
        val processedPathNames = hashSetOf<String>()

        val context = RsCompletionContext(
            element.implLookup,
            getExpectedTypeForEnclosingPathOrDotExpr(element),
            isSimplePath = RsPsiPattern.simplePathPattern.accepts(parameters.position)
        )

        addCompletionVariants(element, result, context, processedPathNames)

        if (element is RsMethodOrField) {
            addMethodAndFieldCompletion(element, result, context)
        }

        if (context.isSimplePath && RsCodeInsightSettings.getInstance().suggestOutOfScopeItems) {
            addCompletionsFromIndex(parameters, result, processedPathNames, context.expectedTy)
        }
    }

    @VisibleForTesting
    fun addCompletionVariants(
        element: RsReferenceElement,
        result: CompletionResultSet,
        context: RsCompletionContext,
        processedPathNames: MutableSet<String>
    ) {
        collectCompletionVariants(result, context) {
            when (element) {
                is RsAssocTypeBinding -> processAssocTypeVariants(element, it)
                is RsExternCrateItem -> processExternCrateResolveVariants(element, true, it)
                is RsLabel -> processLabelResolveVariants(element, it)
                is RsLifetime -> processLifetimeResolveVariants(element, it)
                is RsMacroReference -> processMacroReferenceVariants(element, it)
                is RsModDeclItem -> processModDeclResolveVariants(element, it)
                is RsPatBinding -> processPatBindingResolveVariants(element, true, it)
                is RsStructLiteralField -> processStructLiteralFieldResolveVariants(element, true, it)
                is RsPath -> processPathVariants(element, processedPathNames, it)
            }
        }
    }

    private fun processPathVariants(
        element: RsPath,
        processedPathNames: MutableSet<String>,
        processor: RsResolveProcessor
    ) {
        when (element.parent) {
            is RsMacroCall -> processMacroCallPathResolveVariants(element, true, processor)
            // Handled by [RsDeriveCompletionProvider]
            is RsMetaItem -> return
            else -> {
                val lookup = ImplLookup.relativeTo(element)
                processPathResolveVariants(
                    lookup,
                    element,
                    true,
                    filterAssocTypes(
                        element,
                        filterCompletionVariantsByVisibility(
                            filterPathCompletionVariantsByTraitBounds(
                                addProcessedPathName(processor, processedPathNames),
                                lookup
                            ),
                            element.containingMod
                        )
                    )
                )
            }
        }
    }

    @VisibleForTesting
    fun addMethodAndFieldCompletion(
        element: RsMethodOrField,
        result: CompletionResultSet,
        context: RsCompletionContext
    ) {
        val receiver = element.receiver.safeGetOriginalOrSelf()
        val lookup = ImplLookup.relativeTo(receiver)
        val receiverTy = receiver.type
        val processResolveVariants = if (element is RsMethodCall) {
            ::processMethodCallExprResolveVariants
        } else {
            ::processDotExprResolveVariants
        }
        val processor = methodAndFieldCompletionProcessor(element, result, context)

        processResolveVariants(
            lookup,
            receiverTy,
            element,
            filterCompletionVariantsByVisibility(
                filterMethodCompletionVariantsByTraitBounds(processor, lookup, receiverTy),
                receiver.containingMod
            )
        )
    }

    private fun addCompletionsFromIndex(
        parameters: CompletionParameters,
        result: CompletionResultSet,
        processedPathNames: Set<String>,
        expectedTy: Ty?
    ) {
        val path = run {
            // Not null if delegated from RsMacroCallBodyCompletionProvider
            val positionInMacroArgument = parameters.position.findElementExpandedFrom()
            val originalPosition = if (positionInMacroArgument != null) positionInMacroArgument.safeGetOriginalElement() else parameters.originalPosition
            // We use the position in the original file in order not to process empty paths
            if (originalPosition == null || originalPosition.elementType != RsElementTypes.IDENTIFIER) return
            val actualPosition = if (positionInMacroArgument != null) parameters.position else originalPosition
            // Checks that macro call and expanded element are located in the same modules
            if (positionInMacroArgument != null && !isInSameRustMod(positionInMacroArgument, actualPosition)) {
                return
            }
            actualPosition.parent as? RsPath ?: return
        }
        if (TyPrimitive.fromPath(path) != null) return
        // TODO: implement special rules paths in meta items
        if (path.parent is RsMetaItem) return
        Testmarks.pathCompletionFromIndex.hit()

        val project = parameters.originalFile.project
        val importContext = ImportContext.from(project, path, true)

        val keys = hashSetOf<String>().apply {
            val explicitNames = StubIndex.getInstance().getAllKeys(RsNamedElementIndex.KEY, project)
            val reexportedNames = StubIndex.getInstance().getAllKeys(RsReexportIndex.KEY, project).mapNotNull {
                (it as? ReexportKey.ProducedNameKey)?.name
            }

            addAll(explicitNames)
            addAll(reexportedNames)

            // Filters out path names that have already been added to `result`
            removeAll(processedPathNames)
        }

        val context = RsCompletionContext(path.implLookup, expectedTy, isSimplePath = true)
        for (elementName in result.prefixMatcher.sortMatching(keys)) {
            val candidates = ImportCandidatesCollector.getImportCandidates(importContext, elementName, elementName) {
                !(it.item is RsMod || it.item is RsModDeclItem || it.item.parent is RsMembers)
            }

            candidates
                .distinctBy { it.qualifiedNamedItem.item }
                .map { candidate ->
                    val item = candidate.qualifiedNamedItem.item
                    createLookupElement(
                        scopeEntry = SimpleScopeEntry(elementName, item),
                        context = context,
                        locationString = candidate.info.usePath,
                        insertHandler = object : RsDefaultInsertHandler() {
                            override fun handleInsert(
                                element: RsElement,
                                scopeName: String,
                                context: InsertionContext,
                                item: LookupElement
                            ) {
                                super.handleInsert(element, scopeName, context, item)
                                if (RsCodeInsightSettings.getInstance().importOutOfScopeItems) {
                                    context.commitDocument()
                                    context.getElementOfType<RsElement>()?.let { candidate.import(it) }
                                }
                            }
                        }
                    )
                }
                .forEach(result::addElement)
        }
    }

    private fun isInSameRustMod(element1: PsiElement, element2: PsiElement): Boolean =
        element1.contextOrSelf<RsElement>()?.containingMod ==
            element2.contextOrSelf<RsElement>()?.containingMod

    override val elementPattern: ElementPattern<PsiElement>
        get() = PlatformPatterns.psiElement().withParent(psiElement<RsReferenceElement>())

    object Testmarks {
        val pathCompletionFromIndex = Testmark("pathCompletionFromIndex")
    }
}

data class RsCompletionContext(
    val lookup: ImplLookup? = null,
    val expectedTy: Ty? = null,
    val isSimplePath: Boolean = false
)

private fun filterAssocTypes(
    path: RsPath,
    processor: RsResolveProcessor
): RsResolveProcessor {
    val qualifier = path.path
    val allAssocItemsAllowed =
        qualifier == null || qualifier.hasCself || qualifier.reference?.resolve() is RsTypeParameter
    return if (allAssocItemsAllowed) processor else createProcessor(processor.name) {
        if (it is AssocItemScopeEntry && (it.element is RsTypeAlias)) false
        else processor(it)
    }
}

private fun filterPathCompletionVariantsByTraitBounds(
    processor: RsResolveProcessor,
    lookup: ImplLookup
): RsResolveProcessor {
    val cache = hashMapOf<TraitImplSource, Boolean>()
    return createProcessor(processor.name) {
        if (it !is AssocItemScopeEntry) return@createProcessor processor(it)

        val receiver = it.subst[TyTypeParameter.self()] ?: return@createProcessor processor(it)
        // Don't filter partially unknown types
        if (receiver.containsTyOfClass(TyUnknown::class.java)) return@createProcessor processor(it)
        // Filter members by trait bounds (try to select all obligations for each impl)
        // We're caching evaluation results here because we can often complete members
        // in the same impl and always have the same receiver type
        val canEvaluate = cache.getOrPut(it.source) {
            lookup.ctx.canEvaluateBounds(it.source, receiver)
        }
        if (canEvaluate) return@createProcessor processor(it)

        false
    }
}

private fun filterMethodCompletionVariantsByTraitBounds(
    processor: RsResolveProcessor,
    lookup: ImplLookup,
    receiver: Ty
): RsResolveProcessor {
    // Don't filter partially unknown types
    if (receiver.containsTyOfClass(TyUnknown::class.java)) return processor

    val cache = mutableMapOf<TraitImplSource, Boolean>()
    return createProcessor(processor.name) {
        // If not a method (actually a field) or a trait method - just process it
        if (it !is MethodResolveVariant) return@createProcessor processor(it)
        // Filter methods by trait bounds (try to select all obligations for each impl)
        // We're caching evaluation results here because we can often complete methods
        // in the same impl and always have the same receiver type
        val canEvaluate = cache.getOrPut(it.source) {
            lookup.ctx.canEvaluateBounds(it.source, receiver)
        }
        if (canEvaluate) return@createProcessor processor(it)

        false
    }
}

private fun methodAndFieldCompletionProcessor(
    methodOrField: RsMethodOrField,
    result: CompletionResultSet,
    context: RsCompletionContext
): RsResolveProcessor = createProcessor { e ->
    when (e) {
        is FieldResolveVariant -> result.addElement(createLookupElement(
            scopeEntry = e,
            context = context
        ))
        is MethodResolveVariant -> {
            if (e.element.isTest) return@createProcessor false

            result.addElement(createLookupElement(
                scopeEntry = e,
                context = context,
                insertHandler = object : RsDefaultInsertHandler() {
                    override fun handleInsert(
                        element: RsElement,
                        scopeName: String,
                        context: InsertionContext,
                        item: LookupElement
                    ) {
                        val traitImportCandidate = findTraitImportCandidate(methodOrField, e)
                        super.handleInsert(element, scopeName, context, item)

                        if (traitImportCandidate != null) {
                            context.commitDocument()
                            context.getElementOfType<RsElement>()?.let { traitImportCandidate.import(it) }
                        }
                    }
                }
            ))
        }
    }
    false
}

private fun findTraitImportCandidate(methodOrField: RsMethodOrField, resolveVariant: MethodResolveVariant): ImportCandidate? {
    if (!RsCodeInsightSettings.getInstance().importOutOfScopeItems) return null
    val ancestor = PsiTreeUtil.getParentOfType(methodOrField, RsBlock::class.java, RsMod::class.java) ?: return null
    // `ImportCandidatesCollector.getImportCandidates` expects original scope element for correct item filtering
    val scope = CompletionUtil.getOriginalElement(ancestor) as? RsElement ?: return null
    return ImportCandidatesCollector
        .getImportCandidates(methodOrField.project, scope, listOf(resolveVariant))
        .orEmpty()
        .singleOrNull()
}

private fun addProcessedPathName(
    processor: RsResolveProcessor,
    processedPathNames: MutableSet<String>
): RsResolveProcessor = createProcessor(processor.name) {
    if (it.element != null) {
        processedPathNames.add(it.name)
    }
    processor(it)
}

private fun getExpectedTypeForEnclosingPathOrDotExpr(element: RsReferenceElement): Ty? {
    for (ancestor in element.ancestors) {
        if (element.endOffset < ancestor.endOffset) continue
        if (element.endOffset > ancestor.endOffset) break
        when (ancestor) {
            is RsPathExpr -> return ancestor.expectedType
            is RsDotExpr -> return ancestor.expectedType
        }
    }
    return null
}
