/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.MultiMap
import org.rust.ide.refactoring.RsNamesValidator
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.ide.utils.import.*
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
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.openapiext.Testmark

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
        val processedPathElements = MultiMap<String, RsElement>()

        val context = RsCompletionContext(
            element,
            getExpectedTypeForEnclosingPathOrDotExpr(element),
            isSimplePath = RsPsiPattern.simplePathPattern.accepts(parameters.position)
        )

        addCompletionVariants(element, result, context, processedPathElements)

        if (element is RsMethodOrField) {
            addMethodAndFieldCompletion(element, result, context)
        }

        if (context.isSimplePath && RsCodeInsightSettings.getInstance().suggestOutOfScopeItems) {
            addCompletionsFromIndex(parameters, result, processedPathElements, context.expectedTy)
        }
    }

    @VisibleForTesting
    fun addCompletionVariants(
        element: RsReferenceElement,
        result: CompletionResultSet,
        context: RsCompletionContext,
        processedPathNames: MultiMap<String, RsElement>
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
        processedPathElements: MultiMap<String, RsElement>,
        processor: RsResolveProcessor
    ) {
        val parent = element.parent
        when {
            parent is RsMacroCall -> processMacroCallPathResolveVariants(element, true, processor)
            // Handled by [RsDeriveCompletionProvider]
            parent is RsMetaItem -> return
            // Handled by [RsVisRestrictionCompletionProvider]
            parent is RsVisRestriction && parent.`in` == null -> return
            else -> {
                val lookup = ImplLookup.relativeTo(element)
                var filtered: RsResolveProcessor = filterPathCompletionVariantsByTraitBounds(
                    addProcessedPathName(processor, processedPathElements),
                    lookup
                )
                // Filters are applied in reverse order (the last filter is applied first)
                val filters = listOf(
                    ::filterCompletionVariantsByVisibility,
                    ::filterAssocTypes,
                    ::filterVisRestrictionPaths,
                    ::filterTraitRefPaths
                )
                for (filter in filters) {
                    filtered = filter(element, filtered)
                }

                processPathResolveVariants(
                    lookup,
                    element,
                    true,
                    filtered
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
                receiver,
                filterMethodCompletionVariantsByTraitBounds(
                    lookup,
                    receiverTy,
                    deduplicateMethodCompletionVariants(processor)
                )
            )
        )
    }

    private fun addCompletionsFromIndex(
        parameters: CompletionParameters,
        result: CompletionResultSet,
        processedPathElements: MultiMap<String, RsElement>,
        expectedTy: Ty?
    ) {
        val path = run {
            // Not null if delegated from RsMacroCallBodyCompletionProvider
            val positionInMacroArgument = parameters.position.findElementExpandedFrom()
            val originalPosition = if (positionInMacroArgument != null) positionInMacroArgument.safeGetOriginalElement() else parameters.originalPosition
            // We use the position in the original file in order not to process empty paths
            if (originalPosition == null || originalPosition.elementType != RsElementTypes.IDENTIFIER) {
                result.restartCompletionOnPrefixChange(StandardPatterns.string().withLength(1))
                return
            }
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

        val context = RsCompletionContext(path, expectedTy, isSimplePath = true)
        val candidates = if (path.useAutoImportWithNewResolve) run {
            val importContext = ImportContext2.from(path, ImportContext2.Type.COMPLETION) ?: return@run emptyList()
            ImportCandidatesCollector2.getCompletionCandidates(importContext, result.prefixMatcher, processedPathElements)
        } else {
            val keys = hashSetOf<String>().apply {
                val explicitNames = StubIndex.getInstance().getAllKeys(RsNamedElementIndex.KEY, project)
                val reexportedNames = StubIndex.getInstance().getAllKeys(RsReexportIndex.KEY, project).mapNotNull {
                    (it as? ReexportKey.ProducedNameKey)?.name
                }

                addAll(explicitNames)
                addAll(reexportedNames)

                // Filters out path names that have already been added to `result`
                removeAll(processedPathElements.keySet())
            }

            val importContext = ImportContext.from(project, path, true)
            result.prefixMatcher.sortMatching(keys)
                .flatMap { elementName ->
                    ImportCandidatesCollector.getImportCandidates(importContext, elementName, elementName) {
                        !(it.item is RsMod || it.item is RsModDeclItem || it.item.parent is RsMembers)
                    }
                }
        }

        for (candidate in candidates) {
            val item = candidate.qualifiedNamedItem.item
            val scopeEntry = SimpleScopeEntry(candidate.qualifiedNamedItem.itemName ?: continue, item)

            if (item is RsEnumItem
                && (context.expectedTy?.stripReferences() as? TyAdt)?.item == (item.declaredType as? TyAdt)?.item) {
                val variants = collectVariantsForEnumCompletion(item, context, scopeEntry.subst, candidate)
                result.addAllElements(variants)
            }

            val lookupElement = createLookupElement(
                scopeEntry = scopeEntry,
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
            ).withImportCandidate(candidate)
            result.addElement(lookupElement)
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
    val context: RsElement? = null,
    val expectedTy: Ty? = null,
    val isSimplePath: Boolean = false
) {
    val lookup: ImplLookup? = context?.implLookup
}

/**
 * Ignore items that are not an ancestor module of the given path
 * in path completion inside visibility restriction:
 * `pub(in <here>)`
 */
private fun filterVisRestrictionPaths(
    path: RsPath,
    processor: RsResolveProcessor
): RsResolveProcessor {
    return if (path.parent is RsVisRestriction) {
        val allowedModules = path.containingMod.superMods
        createProcessor(processor.name) {
            when (it.element) {
                !is RsMod -> false
                !in allowedModules -> false
                else -> processor(it)
            }
        }
    } else {
        processor
    }
}

/**
 * Ignore items that are not traits (or modules, which could lead to traits) inside trait refs:
 * `impl /*here*/ for <type>`
 * `fn foo<T: /*here*/>() {}
 */
private fun filterTraitRefPaths(
    path: RsPath,
    processor: RsResolveProcessor
): RsResolveProcessor {
    val parent = path.parent
    return if (parent is RsTraitRef) {
        createProcessor(processor.name) {
            if (it.element is RsTraitItem || it.element is RsMod) {
                processor(it)
            } else {
                false
            }
        }
    } else {
        processor
    }
}

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
    lookup: ImplLookup,
    receiver: Ty,
    processor: RsResolveProcessor
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

/**
 * There can be multiple impls of the same trait on different dereference levels.
 * For example, trait `Debug` is implemented for `str` and for `&str`. In this case
 * we receive completion variants for both `str` and `&str` implementation, but they
 * are absolutely identical for a user.
 *
 * Here we deduplicate method completion variants by a method name and a trait.
 *
 * Should be applied after [filterMethodCompletionVariantsByTraitBounds]
 */
private fun deduplicateMethodCompletionVariants(processor: RsResolveProcessor): RsResolveProcessor {
    val processedNamesAndTraits = mutableSetOf<Pair<String, RsTraitItem?>>()
    return createProcessor(processor.name) {
        if (it !is MethodResolveVariant) return@createProcessor processor(it)
        val shouldProcess = processedNamesAndTraits.add(it.name to it.source.implementedTrait?.element)
        if (shouldProcess) return@createProcessor processor(it)

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

private fun findTraitImportCandidate(methodOrField: RsMethodOrField, resolveVariant: MethodResolveVariant): ImportCandidateBase? {
    if (!RsCodeInsightSettings.getInstance().importOutOfScopeItems) return null
    val ancestor = PsiTreeUtil.getParentOfType(methodOrField, RsBlock::class.java, RsMod::class.java) ?: return null
    // `ImportCandidatesCollector.getImportCandidates` expects original scope element for correct item filtering
    val scope = CompletionUtil.getOriginalElement(ancestor) as? RsElement ?: return null
    val candidates = if (scope.useAutoImportWithNewResolve) {
        ImportCandidatesCollector2.getImportCandidates(scope, listOf(resolveVariant))?.asSequence()
    } else {
        ImportCandidatesCollector.getImportCandidates(methodOrField.project, scope, listOf(resolveVariant))
    }
    return candidates.orEmpty().singleOrNull()
}

private fun addProcessedPathName(
    processor: RsResolveProcessor,
    processedPathElements: MultiMap<String, RsElement>
): RsResolveProcessor = createProcessor(processor.name) {
    val element = it.element
    if (element != null) {
        processedPathElements.putValue(it.name, element)
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

private fun LookupElement.withImportCandidate(candidate: ImportCandidateBase): RsImportLookupElement {
    return RsImportLookupElement(this, candidate)
}

/**
 * Provides [equals] and [hashCode] that take into account the corresponding [ImportCandidate].
 * We need to distinguish lookup elements with the same psi element and the same lookup text
 * but belong to different import candidates, otherwise the platform shows only one such item.
 *
 * See [#5415](https://github.com/intellij-rust/intellij-rust/issues/5415)
 */
private class RsImportLookupElement(
    delegate: LookupElement,
    private val candidate: ImportCandidateBase
) : LookupElementDecorator<LookupElement>(delegate) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as RsImportLookupElement

        if (candidate != other.candidate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + candidate.hashCode()
        return result
    }
}

fun collectVariantsForEnumCompletion(
    element: RsEnumItem,
    context: RsCompletionContext,
    substitution: Substitution,
    candidate: ImportCandidateBase? = null
): List<LookupElement> {
    val enumName = element.name ?: return emptyList()

    return element.enumBody?.childrenOfType<RsEnumVariant>().orEmpty().mapNotNull { enumVariant ->
        val variantName = enumVariant.name ?: return@mapNotNull null

        return@mapNotNull createLookupElement(
            scopeEntry = SimpleScopeEntry("${enumName}::${variantName}", enumVariant, substitution),
            context = context,
            null,
            object : RsDefaultInsertHandler() {
                override fun handleInsert(element: RsElement, scopeName: String, context: InsertionContext, item: LookupElement) {
                    // move start offset from enum to its variant to escape it
                    val enumStartOffset = context.startOffset
                    val variantStartOffset = enumStartOffset + (enumName.length + 2)
                    context.offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, variantStartOffset)
                    super.handleInsert(enumVariant, variantName, context, item)

                    // escape enum name if needed
                    if (element is RsNameIdentifierOwner && !RsNamesValidator.isIdentifier(enumName) && enumName !in CAN_NOT_BE_ESCAPED)
                        context.document.insertString(enumStartOffset, RS_RAW_PREFIX)

                    if (candidate != null && RsCodeInsightSettings.getInstance().importOutOfScopeItems) {
                        context.commitDocument()
                        context.getElementOfType<RsElement>()?.let { it -> candidate.import(it) }
                    }
                }
            }
        ).let { if (candidate != null) it.withImportCandidate(candidate) else it }
    }
}
