/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.MultiMap
import org.rust.ide.refactoring.RsNamesValidator
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.ide.utils.import.*
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.completion.RsLookupElementProperties.ElementKind.FROM_UNRESOLVED_IMPORT
import org.rust.lang.core.macros.findElementExpandedFrom
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psiElement
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.FieldResolveVariant
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.ExpectedType
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.infer.substituteAndNormalizeOrUnknown
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.Testmark
import org.rust.stdext.mapNotNullToSet

object RsCommonCompletionProvider : RsCompletionProvider() {
    override fun addCompletions(
        parameters: CompletionParameters,
        processingContext: ProcessingContext,
        result: CompletionResultSet
    ) {
        // Use original position if possible to re-use caches of the real file
        val position = parameters.position
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

        if (element is RsPath && RsCodeInsightSettings.getInstance().suggestOutOfScopeItems) {
            if (context.isSimplePath && !element.isInsideDocLink) {
                addCompletionsForOutOfScopeItems(position, element, result, processedPathElements, context.expectedTy)
            }

            if (processedPathElements.isEmpty) {
                addCompletionsForOutOfScopeFirstPathSegment(element, result, context)
            }
        }
    }

    @VisibleForTesting
    fun addCompletionVariants(
        element: RsReferenceElement,
        result: CompletionResultSet,
        context: RsCompletionContext,
        processedElements: MultiMap<String, RsElement>
    ) {
        collectCompletionVariants(result, context) {
            val processor = filterNotCfgDisabledItemsAndTestFunctions(it)
            when (element) {
                is RsAssocTypeBinding -> processAssocTypeVariants(element, processor)
                is RsExternCrateItem -> processExternCrateResolveVariants(element, true, processor)
                is RsLabel -> {
                    val processorWithoutLabelsFromBlocks = processor.wrapWithFilter { e ->
                        !(element.parent is RsContExpr && e.element.parent is RsBlockExpr)
                    }
                    processLabelResolveVariants(element, processorWithoutLabelsFromBlocks)
                }
                is RsLifetime -> processLifetimeResolveVariants(element, processor)
                is RsMacroReference -> processMacroReferenceVariants(element, processor)
                is RsModDeclItem -> processModDeclResolveVariants(element, processor)
                is RsPatBinding -> processPatBindingResolveVariants(element, true, processor)
                is RsStructLiteralField -> processStructLiteralFieldResolveVariants(element, true, processor)
                is RsPath -> {
                    val processor2 = addProcessedPathName(processor, processedElements)
                    processPathVariants(element, processor2)
                    processUnresolvedImports(element, result, context)
                }
            }
        }
    }

    private fun processPathVariants(element: RsPath, processor: RsResolveProcessor) {
        val parent = element.parent
        when {
            parent is RsMacroCall -> {
                val filtered = filterNotAttributeAndDeriveProcMacros(processor)
                processMacroCallPathResolveVariants(element, true, filtered)
            }
            parent is RsMetaItem -> {
                // Derive is handled by [RsDeriveCompletionProvider]
                if (!RsProcMacroPsiUtil.canBeProcMacroAttributeCall(parent)) return
                val filtered = filterAttributeProcMacros(processor)
                processProcMacroResolveVariants(element, filtered, isCompletion = true)
            }
            // Handled by [RsVisRestrictionCompletionProvider]
            parent is RsVisRestriction && parent.`in` == null -> return
            else -> {
                val lookup = ImplLookup.relativeTo(element)
                var filtered: RsResolveProcessor = filterPathCompletionVariantsByTraitBounds(processor, lookup)
                if (parent !is RsUseSpeck) {
                    filtered = filterNotAttributeAndDeriveProcMacros(filtered)
                }
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

                // RsPathExpr can become a macro by adding a trailing `!`, so we add macros to completion
                if (element.parent is RsPathExpr && !(element.hasColonColon && element.isAtLeastEdition2018)) {
                    processMacroCallPathResolveVariants(element, isCompletion = true, filtered)
                }

                val possibleTypeArgs = parent?.parent
                if (possibleTypeArgs is RsTypeArgumentList) {
                    val trait = (possibleTypeArgs.parent as? RsPath)?.reference?.resolve() as? RsTraitItem
                    if (trait != null) {
                        processAssocTypeVariants(trait, filtered)
                    }
                }

                processPathResolveVariants(
                    lookup,
                    element,
                    isCompletion = true,
                    processAssocItems = true,
                    filtered
                )
            }
        }
    }

    private fun processUnresolvedImports(path: RsPath, result: CompletionResultSet, context: RsCompletionContext) {
        if (!context.isSimplePath) return

        val unresolvedImports = hashSetOf<String>()
        processUnresolvedImports(path) { useSpeck ->
            val name = useSpeck.nameInScope?.takeIf { it != "_" }
            if (name != null) {
                unresolvedImports += name
            }
        }

        for (unresolvedImport in unresolvedImports) {
            val element = LookupElementBuilder.create(unresolvedImport)
                .toRsLookupElement(RsLookupElementProperties(elementKind = FROM_UNRESOLVED_IMPORT))
            @Suppress("UnstableApiUsage")
            val wrapped = MLRankingIgnorable.wrap(element)
            result.addElement(wrapped)
        }
    }

    @VisibleForTesting
    fun addMethodAndFieldCompletion(
        element: RsMethodOrField,
        result: CompletionResultSet,
        context: RsCompletionContext
    ) {
        val iterMethodInfo = IterMethodInfo(element)
        val processor = MethodsAndFieldsCompletionProcessor(element, result, context)
        addMethodAndFieldCompletionImpl(
            element,
            processor.wrapWithBeforeProcessingHandler(iterMethodInfo::process)
        )

        val iterMethodReturnType = iterMethodInfo.getReturnType(context) ?: return
        addIteratorMethods(element, iterMethodReturnType, context, processor)
    }

    private fun addMethodAndFieldCompletionImpl(element: RsMethodOrField, processor: RsResolveProcessor) {
        val receiver = element.receiver.safeGetOriginalOrSelf()
        val lookup = ImplLookup.relativeTo(receiver)
        val receiverTy = receiver.type
        addMethodAndFieldCompletionImpl(receiverTy, element, lookup, processor)
    }

    private fun addMethodAndFieldCompletionImpl(
        receiverTy: Ty,
        element: RsMethodOrField,
        lookup: ImplLookup,
        processor0: RsResolveProcessor
    ) {
        val processResolveVariants = if (element is RsMethodCall) {
            ::processMethodCallExprResolveVariants
        } else {
            ::processDotExprResolveVariants
        }
        var processor = processor0
        processor = deduplicateMethodCompletionVariants(processor)
        processor = filterMethodCompletionVariantsByTraitBounds(lookup, receiverTy, processor)
        processor = ImportCandidatesCollector.filterAccessibleTraits(element, processor)
        processor = filterCompletionVariantsByVisibility(element, processor)

        processResolveVariants(lookup, receiverTy, element, processor)
    }

    private class IterMethodInfo(private val element: RsMethodOrField) {
        private val entries: MutableList<MethodResolveVariant> = mutableListOf()

        fun process(entry: ScopeEntry) {
            if (entry.name == "iter" && entry is MethodResolveVariant) {
                entries += entry
            }
        }

        fun getReturnType(context: RsCompletionContext): Ty? {
            // Add `.iter().something()` only for single `.iter()` method which doesn't require trait to import
            val traitsInScope = entries
                .mapNotNullToSet { it.source.requiredTraitInScope }
                .filterInScope(element)
                .toHashSet()
            val entry = entries.singleOrNull {
                val trait = it.source.requiredTraitInScope
                trait == null || trait in traitsInScope
            } ?: return null

            val lookup = context.lookup ?: return null
            val subst = lookup.ctx.getSubstitution(entry)
            val returnType = entry.element.rawReturnType.substituteAndNormalizeOrUnknown(subst, lookup.ctx)

            if (returnType is TyUnknown || returnType is TyUnit) return null
            val iterator = lookup.items.Iterator ?: return null
            if (!lookup.canSelectWithDeref(TraitRef(returnType, iterator.withSubst()))) return null

            return returnType
        }
    }

    private fun addIteratorMethods(
        element: RsMethodOrField,
        iterMethodReturnType: Ty,
        context: RsCompletionContext,
        originalProcessor: MethodsAndFieldsCompletionProcessor
    ) {
        val processor = createProcessor { entry ->
            if (entry !is MethodResolveVariant) return@createProcessor
            val entryWithIterPrefix = entry.copy(name = "iter().${entry.name}")
            originalProcessor.process(entryWithIterPrefix)
        }
        addMethodAndFieldCompletionImpl(iterMethodReturnType, element, context.lookup ?: return, processor)
    }

    private fun addCompletionsForOutOfScopeItems(
        position: PsiElement,
        path: RsPath,
        result: CompletionResultSet,
        processedPathElements: MultiMap<String, RsElement>,
        expectedTy: ExpectedType?
    ) {
        run {
            val originalFile = position.containingFile.originalFile
            // true if delegated from RsPartialMacroArgumentCompletionProvider
            val ignoreCodeFragment = originalFile is RsExpressionCodeFragment
                && originalFile.getUserData(FORCE_OUT_OF_SCOPE_COMPLETION) != true
            if (ignoreCodeFragment) return

            // Not null if delegated from RsMacroCallBodyCompletionProvider
            val positionInMacroArgument = position.findElementExpandedFrom()

            // Checks that macro call and expanded element are located in the same modules
            if (positionInMacroArgument != null && !isInSameRustMod(positionInMacroArgument, position)) {
                return
            }
        }
        if (TyPrimitive.fromPath(path) != null) return
        val parent = path.parent
        if (parent is RsMetaItem && !RsProcMacroPsiUtil.canBeProcMacroAttributeCall(parent)) return
        Testmarks.OutOfScopeItemsCompletion.hit()

        val context = RsCompletionContext(path, expectedTy, isSimplePath = true)
        val importContext = ImportContext.from(path, ImportContext.Type.COMPLETION) ?: return
        val candidates = ImportCandidatesCollector.getCompletionCandidates(importContext, result.prefixMatcher, processedPathElements)

        val contextMod = path.containingMod

        for (candidate in candidates) {
            val item = candidate.item
            if (item is RsOuterAttributeOwner) {
                val isHidden = item.shouldHideElementInCompletion(path, contextMod)
                if (isHidden) continue
            }
            val scopeEntry = SimpleScopeEntry(candidate.itemName, item, TYPES_N_VALUES_N_MACROS)

            if (item is RsEnumItem
                && (context.expectedTy?.ty?.stripReferences() as? TyAdt)?.item == (item.declaredType as? TyAdt)?.item) {
                val variants = collectVariantsForEnumCompletion(item, context, scopeEntry.subst, candidate)
                result.addAllElements(variants)
            }

            val lookupElement = createLookupElementWithImportCandidate(scopeEntry, context, candidate)
            result.addElement(lookupElement)
        }
    }

    /** Adds completions for the case `HashMap::/*caret*/` where `HashMap` is not imported. */
    private fun addCompletionsForOutOfScopeFirstPathSegment(path: RsPath, result: CompletionResultSet, context: RsCompletionContext) {
        val qualifier = path.path ?: return
        val isApplicablePath = (qualifier.path == null && qualifier.typeQual == null && !qualifier.hasColonColon
            && qualifier.resolveStatus == PathResolveStatus.UNRESOLVED)
        if (!isApplicablePath) return

        // We don't use `Type.COMPLETION` because we're importing the first segment of the 2-segment path,
        // so we don't want to relax the namespace filter
        val importContext = ImportContext.from(qualifier, ImportContext.Type.AUTO_IMPORT) ?: return

        val referenceName = qualifier.referenceName ?: return
        val itemToCandidates = ImportCandidatesCollector.getImportCandidates(importContext, referenceName)
            .groupBy { it.item }
        for ((_, candidates) in itemToCandidates) {
            // Here all use path resolves to the same item, so we can just use the first of them
            val firstUsePath = candidates.first().info.usePath

            val newPath = RsCodeFragmentFactory(path.project).createPathInTmpMod(
                path.text,
                importContext.rootMod,
                path.pathParsingMode,
                path.allowedNamespaces(isCompletion = true),
                firstUsePath,
                null
            )

            if (newPath != null) {
                val collector = createProcessor { e ->
                    for (candidate in candidates) {
                        result.addElement(createLookupElementWithImportCandidate(e, context, candidate))
                    }
                }
                val processor = filterCompletionVariantsByVisibility(
                    path,
                    filterNotCfgDisabledItemsAndTestFunctions(collector)
                )
                processPathVariants(newPath, processor)
            }
        }
    }

    private fun createLookupElementWithImportCandidate(
        scopeEntry: ScopeEntry,
        context: RsCompletionContext,
        candidate: ImportCandidate
    ): RsImportLookupElement {
        return createLookupElement(
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
                    context.import(candidate)
                }
            }
        ).withImportCandidate(candidate)
    }

    private fun isInSameRustMod(element1: PsiElement, element2: PsiElement): Boolean =
        element1.contextOrSelf<RsElement>()?.containingMod ==
            element2.contextOrSelf<RsElement>()?.containingMod

    override val elementPattern: ElementPattern<PsiElement>
        get() = PlatformPatterns.psiElement().withParent(psiElement<RsReferenceElement>())

    object Testmarks {
        object OutOfScopeItemsCompletion : Testmark()
    }
}

data class RsCompletionContext(
    val context: RsElement? = null,
    val expectedTy: ExpectedType? = null,
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
        processor.wrapWithFilter {
            when (it.element) {
                !is RsMod -> false
                !in allowedModules -> false
                else -> true
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
        processor.wrapWithFilter {
            it.element is RsTraitItem || it.element is RsMod
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
    return if (allAssocItemsAllowed) processor else processor.wrapWithFilter {
        !(it is AssocItemScopeEntry && (it.element is RsTypeAlias))
    }
}

private fun filterPathCompletionVariantsByTraitBounds(
    processor: RsResolveProcessor,
    lookup: ImplLookup
): RsResolveProcessor {
    val cache = hashMapOf<TraitImplSource, Boolean>()
    return processor.wrapWithFilter {
        if (it !is AssocItemScopeEntry) return@wrapWithFilter true

        val receiver = it.subst[TyTypeParameter.self()] ?: return@wrapWithFilter true
        // Don't filter partially unknown types
        if (receiver.containsTyOfClass(TyUnknown::class.java)) return@wrapWithFilter true
        // Filter members by trait bounds (try to select all obligations for each impl)
        // We're caching evaluation results here because we can often complete members
        // in the same impl and always have the same receiver type
        cache.getOrPut(it.source) {
            lookup.ctx.canEvaluateBounds(it.source, receiver)
        }
    }
}

private fun filterMethodCompletionVariantsByTraitBounds(
    lookup: ImplLookup,
    receiver: Ty,
    processor: RsResolveProcessor
): RsResolveProcessor {
    // Don't filter partially unknown types
    if (receiver.containsTyOfClass(TyUnknown::class.java)) return processor

    val cache = mutableMapOf<Pair<TraitImplSource, Int>, Boolean>()
    return processor.wrapWithFilter {
        // If not a method (actually a field) or a trait method - just process it
        if (it !is MethodResolveVariant) return@wrapWithFilter true
        // Filter methods by trait bounds (try to select all obligations for each impl)
        // We're caching evaluation results here because we can often complete methods
        // in the same impl and always have the same receiver type
        cache.getOrPut(it.source to it.derefCount) {
            lookup.ctx.canEvaluateBounds(it.source, it.selfTy)
        }
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
    return processor.wrapWithFilter {
        if (it !is MethodResolveVariant) return@wrapWithFilter true
        processedNamesAndTraits.add(it.name to it.source.implementedTrait?.element)
    }
}

private class MethodsAndFieldsCompletionProcessor(
    private val methodOrField: RsMethodOrField,
    private val result: CompletionResultSet,
    private val context: RsCompletionContext
) : RsResolveProcessorBase<ScopeEntry> {
    override val names: Set<String>?
        get() = null

    override fun process(entry: ScopeEntry): Boolean {
        when (entry) {
            is FieldResolveVariant -> result.addElement(createLookupElement(
                scopeEntry = entry,
                context = context
            ))
            is MethodResolveVariant -> {
                if (entry.element.isTest) return false

                result.addElement(createLookupElement(
                    scopeEntry = entry,
                    context = context,
                    insertHandler = object : RsDefaultInsertHandler() {
                        override fun handleInsert(
                            element: RsElement,
                            scopeName: String,
                            context: InsertionContext,
                            item: LookupElement
                        ) {
                            val traitImportCandidate = findTraitImportCandidate(methodOrField, entry)
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
        return false
    }
}

private fun findTraitImportCandidate(methodOrField: RsMethodOrField, resolveVariant: MethodResolveVariant): ImportCandidate? {
    if (!RsCodeInsightSettings.getInstance().importOutOfScopeItems) return null
    val ancestor = PsiTreeUtil.getParentOfType(methodOrField, RsBlock::class.java, RsMod::class.java) ?: return null
    // `ImportCandidatesCollector.getImportCandidates` expects original scope element for correct item filtering
    val scope = CompletionUtil.getOriginalElement(ancestor) as? RsElement ?: return null
    val candidates = ImportCandidatesCollector.getImportCandidates(scope, listOf(resolveVariant))?.asSequence()
    return candidates.orEmpty().singleOrNull()
}

private fun addProcessedPathName(
    processor: RsResolveProcessor,
    processedPathElements: MultiMap<String, RsElement>
): RsResolveProcessor = processor.wrapWithBeforeProcessingHandler {
    processedPathElements.putValue(it.name, it.element)
}

private fun getExpectedTypeForEnclosingPathOrDotExpr(element: RsReferenceElement): ExpectedType? {
    for (ancestor in element.ancestors) {
        if (element.endOffset < ancestor.endOffset) continue
        if (element.endOffset > ancestor.endOffset) break
        when (ancestor) {
            is RsPathExpr -> return ancestor.expectedTypeCoercable
            is RsDotExpr -> return ancestor.expectedTypeCoercable
        }
    }
    return null
}

fun LookupElement.withImportCandidate(candidate: ImportCandidate): RsImportLookupElement {
    return RsImportLookupElement(this, candidate)
}

/**
 * Provides [equals] and [hashCode] that take into account the corresponding [ImportCandidate].
 * We need to distinguish lookup elements with the same psi element and the same lookup text
 * but belong to different import candidates, otherwise the platform shows only one such item.
 *
 * See [#5415](https://github.com/intellij-rust/intellij-rust/issues/5415)
 */
class RsImportLookupElement(
    delegate: LookupElement,
    private val candidate: ImportCandidate
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
    candidate: ImportCandidate? = null
): List<LookupElement> {
    val enumName = element.name ?: return emptyList()
    val contextElement = context.context
    val contextMod = contextElement?.containingMod

    return element.enumBody?.childrenOfType<RsEnumVariant>().orEmpty().mapNotNull { enumVariant ->
        val variantName = enumVariant.name ?: return@mapNotNull null

        if (contextMod != null && enumVariant.shouldHideElementInCompletion(contextElement, contextMod)) return@mapNotNull null

        return@mapNotNull createLookupElement(
            scopeEntry = SimpleScopeEntry("${enumName}::${variantName}", enumVariant, ENUM_VARIANT_NS, substitution),
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
                    if (element is RsNameIdentifierOwner && !RsNamesValidator.isIdentifier(enumName) && enumName.canBeEscaped)
                        context.document.insertString(enumStartOffset, RS_RAW_PREFIX)

                    if (candidate != null && RsCodeInsightSettings.getInstance().importOutOfScopeItems) {
                        context.commitDocument()
                        context.getElementOfType<RsElement>()?.let { candidate.import(it) }
                    }
                }
            }
        ).let { if (candidate != null) it.withImportCandidate(candidate) else it }
    }
}

fun InsertionContext.import(candidate: ImportCandidate) {
    if (RsCodeInsightSettings.getInstance().importOutOfScopeItems) {
        commitDocument()
        getElementOfType<RsElement>()?.let { candidate.import(it) }
    }
}
