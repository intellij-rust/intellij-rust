/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.util.SmartList
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.RustChannel
import org.rust.lang.core.completion.RsCompletionContext
import org.rust.lang.core.completion.collectVariantsForEnumCompletion
import org.rust.lang.core.completion.createLookupElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.resolve2.RsModInfo
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.hasTyInfer
import org.rust.lang.core.types.ty.*
import org.rust.stdext.intersects

/**
 * ScopeEntry is some PsiElement visible in some code scope.
 *
 * [ScopeEntry] handles the two case:
 *   * aliases (that's why we need a [name] property)
 *   * lazy resolving of actual elements (that's why [element] can return `null`)
 */
interface ScopeEntry {
    val name: String
    val element: RsElement
    val namespaces: Set<Namespace>
    val subst: Substitution get() = emptySubstitution
    fun doCopyWithNs(namespaces: Set<Namespace>): ScopeEntry
}

@Suppress("UNCHECKED_CAST")
private fun <T: ScopeEntry> T.copyWithNs(namespaces: Set<Namespace>): T = doCopyWithNs(namespaces) as T

typealias RsProcessor<T> = (T) -> Boolean

interface RsResolveProcessorBase<in T : ScopeEntry> {
    /**
     * Return `true` to stop further processing,
     * return `false` to continue search
     */
    fun process(entry: T): Boolean

    /**
     * Indicates that processor is interested only in [ScopeEntry]s with specified [names].
     * Improves performance for Resolve2.
     * `null` in completion
     */
    val names: Set<String>?

    fun acceptsName(name: String): Boolean {
        val names = names
        return names == null || name in names
    }
}

typealias RsResolveProcessor = RsResolveProcessorBase<ScopeEntry>

fun createStoppableProcessor(processor: (ScopeEntry) -> Boolean): RsResolveProcessor {
    return object : RsResolveProcessorBase<ScopeEntry> {
        override fun process(entry: ScopeEntry): Boolean = processor(entry)
        override val names: Set<String>? get() = null
    }
}

fun createProcessor(processor: (ScopeEntry) -> Unit): RsResolveProcessor {
    return object : RsResolveProcessorBase<ScopeEntry> {
        override fun process(entry: ScopeEntry): Boolean {
            processor(entry)
            return false
        }
        override val names: Set<String>? get() = null
    }
}

fun <T : ScopeEntry, U : ScopeEntry> RsResolveProcessorBase<T>.wrapWithMapper(
    mapper: (U) -> T
): RsResolveProcessorBase<U> {
    return MappingProcessor(this, mapper)
}

private class MappingProcessor<in T : ScopeEntry, in U : ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val mapper: (U) -> T,
) : RsResolveProcessorBase<U> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: U): Boolean {
        val mapped = mapper(entry)
        return originalProcessor.process(mapped)
    }
    override fun toString(): String = "MappingProcessor($originalProcessor, mapper = $mapper)"
}

fun <T : ScopeEntry, U : ScopeEntry> RsResolveProcessorBase<T>.wrapWithNonNullMapper(
    mapper: (U) -> T?
): RsResolveProcessorBase<U> {
    return NonNullMappingProcessor(this, mapper)
}

private class NonNullMappingProcessor<in T : ScopeEntry, in U : ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val mapper: (U) -> T?,
) : RsResolveProcessorBase<U> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: U): Boolean {
        val mapped = mapper(entry)
        return if (mapped == null) {
            false
        } else {
            originalProcessor.process(mapped)
        }
    }
    override fun toString(): String = "MappingProcessor($originalProcessor, mapper = $mapper)"
}

fun <T : ScopeEntry> RsResolveProcessorBase<T>.wrapWithFilter(
    filter: (T) -> Boolean
): RsResolveProcessorBase<T> {
    return FilteringProcessor(this, filter)
}

private class FilteringProcessor<in T : ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val filter: (T) -> Boolean,
) : RsResolveProcessorBase<T> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: T): Boolean {
        return if (filter(entry)) {
            originalProcessor.process(entry)
        } else {
            false
        }
    }
    override fun toString(): String = "FilteringProcessor($originalProcessor, filter = $filter)"
}

fun <T : ScopeEntry> RsResolveProcessorBase<T>.wrapWithBeforeProcessingHandler(
    handler: (T) -> Unit
): RsResolveProcessorBase<T> {
    return BeforeProcessingProcessor(this, handler)
}

private class BeforeProcessingProcessor<in T : ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val handler: (T) -> Unit,
) : RsResolveProcessorBase<T> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: T): Boolean {
        handler(entry)
        return originalProcessor.process(entry)
    }
    override fun toString(): String = "BeforeProcessingProcessor($originalProcessor, handler = $handler)"
}

fun <T : ScopeEntry> RsResolveProcessorBase<T>.wrapWithShadowingProcessor(
    prevScope: Map<String, Set<Namespace>>,
    ns: Set<Namespace>,
): RsResolveProcessorBase<T> {
    return ShadowingProcessor(this, prevScope, ns)
}

private class ShadowingProcessor<in T : ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val prevScope: Map<String, Set<Namespace>>,
    private val ns: Set<Namespace>,
) : RsResolveProcessorBase<T> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: T): Boolean {
        val prevNs = prevScope[entry.name]
        if (entry.name == "_" || prevNs == null) return originalProcessor.process(entry)
        val restNs = entry.namespaces.minus(prevNs)
        return ns.intersects(restNs) && originalProcessor.process(entry.copyWithNs(restNs))
    }
    override fun toString(): String = "ShadowingProcessor($originalProcessor, ns = $ns)"
}

fun <T : ScopeEntry> RsResolveProcessorBase<T>.wrapWithShadowingProcessorAndUpdateScope(
    prevScope: Map<String, Set<Namespace>>,
    currScope: MutableMap<String, Set<Namespace>>,
    ns: Set<Namespace>,
): RsResolveProcessorBase<T> {
    return ShadowingAndUpdateScopeProcessor(this, prevScope, currScope, ns)
}

private class ShadowingAndUpdateScopeProcessor<in T : ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val prevScope: Map<String, Set<Namespace>>,
    private val currScope: MutableMap<String, Set<Namespace>>,
    private val ns: Set<Namespace>,
) : RsResolveProcessorBase<T> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: T): Boolean {
        if (!originalProcessor.acceptsName(entry.name) || entry.name == "_") {
            return originalProcessor.process(entry)
        }
        val prevNs = prevScope[entry.name]
        val newNs = entry.namespaces
        val entryWithIntersectedNs = if (prevNs != null) {
            val restNs = newNs.minus(prevNs)
            if (ns.intersects(restNs)) {
                entry.copyWithNs(restNs)
            } else {
                return false
            }
        } else {
            entry
        }
        currScope[entry.name] = prevNs?.let { it + newNs } ?: newNs
        return originalProcessor.process(entryWithIntersectedNs)
    }
    override fun toString(): String = "ShadowingAndUpdateScopeProcessor($originalProcessor, ns = $ns)"
}

fun <T : ScopeEntry> RsResolveProcessorBase<T>.wrapWithShadowingProcessorAndImmediatelyUpdateScope(
    prevScope: MutableMap<String, Set<Namespace>>,
    ns: Set<Namespace>,
): RsResolveProcessorBase<T> {
    return ShadowingAndImmediatelyUpdateScopeProcessor(this, prevScope, ns)
}

private class ShadowingAndImmediatelyUpdateScopeProcessor<in T : ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val prevScope: MutableMap<String, Set<Namespace>>,
    private val ns: Set<Namespace>,
) : RsResolveProcessorBase<T> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: T): Boolean {
        if (entry.name in prevScope) return false
        val result = originalProcessor.process(entry)
        if (originalProcessor.acceptsName(entry.name)) {
            prevScope[entry.name] = ns
        }
        return result
    }
    override fun toString(): String = "ShadowingAndImmediatelyUpdateScopeProcessor($originalProcessor, ns = $ns)"
}

typealias RsMethodResolveProcessor = RsResolveProcessorBase<MethodResolveVariant>

fun collectPathResolveVariants(
    ctx: PathResolutionContext,
    path: RsPath,
    f: (RsResolveProcessor) -> Unit
): List<RsPathResolveResult<RsElement>> {
    val referenceName = path.referenceName ?: return emptyList()
    val processor = SinglePathResolveVariantsCollector(ctx, referenceName)
    f(processor)
    return processor.result
}

private class SinglePathResolveVariantsCollector(
    private val ctx: PathResolutionContext,
    private val referenceName: String,
    val result: MutableList<RsPathResolveResult<RsElement>> = SmartList(),
) : RsResolveProcessorBase<ScopeEntry> {
    override val names: Set<String> = setOf(referenceName)

    override fun process(entry: ScopeEntry): Boolean {
        if (entry.name == referenceName) {
            collectPathScopeEntry(ctx, result, entry)
        }
        return false
    }
}

fun collectMultiplePathResolveVariants(
    ctx: PathResolutionContext,
    paths: List<RsPath>,
    f: (RsResolveProcessor) -> Unit
): Map<RsPath, SmartList<RsPathResolveResult<RsElement>>> {
    val result: MutableMap<RsPath, SmartList<RsPathResolveResult<RsElement>>> = hashMapOf()
    val resultByName: MutableMap<String, SmartList<RsPathResolveResult<RsElement>>> = hashMapOf()
    for (path in paths) {
        val name = path.referenceName ?: continue
        val list = resultByName.getOrPut(name) { SmartList() }
        result[path] = list
    }
    val processor = MultiplePathsResolveVariantsCollector(ctx, resultByName)
    f(processor)
    return result
}

private class MultiplePathsResolveVariantsCollector(
    private val ctx: PathResolutionContext,
    private val resultByName: MutableMap<String, SmartList<RsPathResolveResult<RsElement>>>,
) : RsResolveProcessorBase<ScopeEntry> {
    override val names: Set<String> = resultByName.keys

    override fun process(entry: ScopeEntry): Boolean {
        val list = resultByName[entry.name]
        if (list != null) {
            collectPathScopeEntry(ctx, list, entry)
        }
        return false
    }
}

private fun collectPathScopeEntry(
    ctx: PathResolutionContext,
    result: MutableList<RsPathResolveResult<RsElement>>,
    e: ScopeEntry
) {
    val element = e.element
    if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
        val visibilityStatus = e.getVisibilityStatusFrom(ctx.context, ctx.lazyContainingModInfo)
        if (visibilityStatus != VisibilityStatus.CfgDisabled) {
            val isVisible = visibilityStatus == VisibilityStatus.Visible
            // Canonicalize namespaces to consume less memory by the resolve cache
            val namespaces = when (e.namespaces) {
                TYPES -> TYPES
                VALUES -> VALUES
                TYPES_N_VALUES -> TYPES_N_VALUES
                else -> e.namespaces
            }
            result += RsPathResolveResult(element, e.subst.foldTyInferWithTyPlaceholder(), isVisible, namespaces)
        }
    }
}
// This is basically a hack - we replace type variables incorrectly created during name resolution
// TODO don't create `TyInfer.TyVar` during name resolution
private fun Substitution.foldTyInferWithTyPlaceholder(): Substitution =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty {
            val foldedTy = if (ty is TyInfer.TyVar) {
                if (ty.origin is RsInferType) {
                    TyPlaceholder(ty.origin)
                } else {
                    TyUnknown
                }
            } else {
                ty
            }
            return if (foldedTy.hasTyInfer) foldedTy.superFoldWith(this) else foldedTy
        }
    })

fun collectResolveVariants(referenceName: String?, f: (RsResolveProcessor) -> Unit): List<RsElement> {
    if (referenceName == null) return emptyList()
    val processor = ResolveVariantsCollector(referenceName)
    f(processor)
    return processor.result
}

private class ResolveVariantsCollector(
    private val referenceName: String,
    val result: MutableList<RsElement> = SmartList(),
) : RsResolveProcessorBase<ScopeEntry> {
    override val names: Set<String> = setOf(referenceName)

    override fun process(entry: ScopeEntry): Boolean {
        if (entry.name == referenceName) {
            val element = entry.element
            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
                result += element
            }
        }
        return false
    }
}

fun <T : ScopeEntry> collectResolveVariantsAsScopeEntries(
    referenceName: String?,
    f: (RsResolveProcessorBase<T>) -> Unit
): List<T> {
    if (referenceName == null) return emptyList()
    val processor = ResolveVariantsAsScopeEntriesCollector<T>(referenceName)
    f(processor)
    return processor.result
}

private class ResolveVariantsAsScopeEntriesCollector<T: ScopeEntry>(
    private val referenceName: String,
    val result: MutableList<T> = mutableListOf(),
) : RsResolveProcessorBase<T> {
    override val names: Set<String> = setOf(referenceName)

    override fun process(entry: T): Boolean {
        if (entry.name == referenceName) {
            val element = entry.element
            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
                result += entry
            }
        }
        return false
    }
}

fun pickFirstResolveVariant(referenceName: String?, f: (RsResolveProcessor) -> Unit): RsElement? =
    pickFirstResolveEntry(referenceName, f)?.element

fun pickFirstResolveEntry(referenceName: String?, f: (RsResolveProcessor) -> Unit): ScopeEntry? {
    if (referenceName == null) return null
    val processor = PickFirstScopeEntryCollector(referenceName)
    f(processor)
    return processor.result
}

private class PickFirstScopeEntryCollector(
    private val referenceName: String,
    var result: ScopeEntry? = null,
) : RsResolveProcessorBase<ScopeEntry> {
    override val names: Set<String> = setOf(referenceName)

    override fun process(entry: ScopeEntry): Boolean {
        if (entry.name == referenceName) {
            val element = entry.element
            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
                result = entry
                return true
            }
        }
        return false
    }
}

fun collectCompletionVariants(
    result: CompletionResultSet,
    context: RsCompletionContext,
    f: (RsResolveProcessor) -> Unit
) {
    val processor = CompletionVariantsCollector(result, context)
    f(processor)
}

private class CompletionVariantsCollector(
    private val result: CompletionResultSet,
    private val context: RsCompletionContext,
) : RsResolveProcessorBase<ScopeEntry> {
    override val names: Set<String>? get() = null

    override fun process(entry: ScopeEntry): Boolean {
        addEnumVariantsIfNeeded(entry)
        addAssociatedItemsIfNeeded(entry)

        result.addElement(createLookupElement(
            scopeEntry = entry,
            context = context
        ))
        return false
    }

    private fun addEnumVariantsIfNeeded(entry: ScopeEntry) {
        val element = entry.element as? RsEnumItem ?: return

        val expectedType = (context.expectedTy?.ty?.stripReferences() as? TyAdt)?.item
        val actualType = (element.declaredType as? TyAdt)?.item

        val parent = context.context
        val contextPat = if (parent is RsPath) parent.context else parent
        val contextIsPat = contextPat is RsPatBinding || contextPat is RsPatStruct || contextPat is RsPatTupleStruct

        if (expectedType == actualType || contextIsPat) {
            val variants = collectVariantsForEnumCompletion(element, context, entry.subst)
            val filtered = when (contextPat) {
                is RsPatStruct -> variants.filter { (it.psiElement as? RsEnumVariant)?.blockFields != null }
                is RsPatTupleStruct -> variants.filter { (it.psiElement as? RsEnumVariant)?.tupleFields != null }
                else -> variants
            }
            result.addAllElements(filtered)
        }
    }

    private fun addAssociatedItemsIfNeeded(entry: ScopeEntry) {
        if (entry.name != "Self") return
        val entryTrait = when (val traitOrImpl = entry.element as? RsTraitOrImpl) {
            is RsTraitItem -> traitOrImpl as? RsTraitItem ?: return
            is RsImplItem -> traitOrImpl.traitRef?.path?.reference?.resolve() as? RsTraitItem ?: return
            else -> return
        }

        val associatedTypes = entryTrait
            .associatedTypesTransitively
            .mapNotNull { type ->
                val name = type.name ?: return@mapNotNull null
                val typeAlias = type.superItem ?: type
                createLookupElement(
                    SimpleScopeEntry("Self::$name", typeAlias, TYPES),
                    context,
                )
            }
        result.addAllElements(associatedTypes)
    }
}

fun collectNames(f: (RsResolveProcessor) -> Unit): Set<String> {
    val processor = NamesCollector()
    f(processor)
    return processor.result
}

private class NamesCollector(
    val result: MutableSet<String> = mutableSetOf(),
) : RsResolveProcessorBase<ScopeEntry> {
    override val names: Set<String>? get() = null

    override fun process(entry: ScopeEntry): Boolean {
        if (entry.name != "_") {
            result += entry.name
        }
        return false
    }
}

data class SimpleScopeEntry(
    override val name: String,
    override val element: RsElement,
    override val namespaces: Set<Namespace>,
    override val subst: Substitution = emptySubstitution
) : ScopeEntry {
    override fun doCopyWithNs(namespaces: Set<Namespace>): ScopeEntry = copy(namespaces = namespaces)
}

data class ScopeEntryWithVisibility(
    override val name: String,
    override val element: RsElement,
    override val namespaces: Set<Namespace>,
    /** Given a [RsElement] (usually [RsPath]) checks if this item is visible in `containingMod` of that element */
    val visibilityFilter: VisibilityFilter,
    override val subst: Substitution = emptySubstitution,
) : ScopeEntry {
    override fun doCopyWithNs(namespaces: Set<Namespace>): ScopeEntry = copy(namespaces = namespaces)
}

typealias VisibilityFilter = (RsElement, Lazy<RsModInfo?>?) -> VisibilityStatus

fun ScopeEntry.getVisibilityStatusFrom(context: RsElement, lazyModInfo: Lazy<RsModInfo?>?): VisibilityStatus =
    if (this is ScopeEntryWithVisibility) {
        visibilityFilter(context, lazyModInfo)
    } else {
        VisibilityStatus.Visible
    }

fun ScopeEntry.isVisibleFrom(context: RsElement): Boolean =
    getVisibilityStatusFrom(context, null) == VisibilityStatus.Visible

enum class VisibilityStatus {
    Visible,
    Invisible,
    CfgDisabled,
}

interface AssocItemScopeEntryBase<out T : RsAbstractable> : ScopeEntry {
    override val element: T
    val selfTy: Ty
    val source: TraitImplSource
}

data class AssocItemScopeEntry(
    override val name: String,
    override val element: RsAbstractable,
    override val namespaces: Set<Namespace>,
    override val subst: Substitution,
    override val selfTy: Ty,
    override val source: TraitImplSource
) : AssocItemScopeEntryBase<RsAbstractable> {
    override fun doCopyWithNs(namespaces: Set<Namespace>): ScopeEntry = copy(namespaces = namespaces)
}


fun RsResolveProcessor.process(name: String, namespaces: Set<Namespace>, e: RsElement): Boolean =
    process(SimpleScopeEntry(name, e, namespaces))

fun RsResolveProcessor.process(
    name: String,
    e: RsElement,
    namespaces: Set<Namespace>,
    visibilityFilter: VisibilityFilter
): Boolean = process(ScopeEntryWithVisibility(name, e, namespaces, visibilityFilter))

inline fun RsResolveProcessor.lazy(name: String, namespaces: Set<Namespace>, e: () -> RsElement?): Boolean {
    if (!acceptsName(name)) return false
    val element = e() ?: return false
    return process(name, namespaces, element)
}

fun RsResolveProcessor.process(e: RsNamedElement, namespaces: Set<Namespace>): Boolean {
    val name = e.name ?: return false
    return process(name, namespaces, e)
}

fun RsResolveProcessor.processAll(elements: List<RsNamedElement>, namespaces: Set<Namespace>): Boolean {
    return elements.any { process(it, namespaces) }
}

fun processAllScopeEntries(elements: List<ScopeEntry>, processor: RsResolveProcessor): Boolean {
    return elements.any { processor.process(it) }
}

fun processAllWithSubst(
    elements: Collection<RsNamedElement>,
    subst: Substitution,
    namespaces: Set<Namespace>,
    processor: RsResolveProcessor
): Boolean {
    for (e in elements) {
        val name = e.name ?: continue
        if (processor.process(SimpleScopeEntry(name, e, namespaces, subst))) return true
    }
    return false
}

fun filterNotCfgDisabledItemsAndTestFunctions(processor: RsResolveProcessor): RsResolveProcessor {
    return processor.wrapWithFilter { e ->
        val element = e.element
        if (element is RsFunction && element.isTest) return@wrapWithFilter false
        if (element is RsDocAndAttributeOwner && !element.existsAfterExpansionSelf) return@wrapWithFilter false

        true
    }
}

fun filterCompletionVariantsByVisibility(context: RsElement, processor: RsResolveProcessor): RsResolveProcessor {
    // Do not filter out private items in debugger
    if (context.containingFile is RsDebuggerExpressionCodeFragment) {
        return processor
    }
    val contextMod = context.containingMod
    return processor.wrapWithFilter {
        val element = it.element
        if (element is RsVisible && !element.isVisibleFrom(contextMod)) return@wrapWithFilter false
        if (!it.isVisibleFrom(context)) return@wrapWithFilter false

        if (element is RsOuterAttributeOwner) {
            val isHidden = element.shouldHideElementInCompletion(context, contextMod)
            if (isHidden) return@wrapWithFilter false
        }

        true
    }
}

fun filterNotAttributeAndDeriveProcMacros(processor: RsResolveProcessor): RsResolveProcessor =
    processor.wrapWithFilter { e ->
        val element = e.element
        if (element is RsFunction && element.isProcMacroDef && !element.isBangProcMacroDef) return@wrapWithFilter false
        true
    }

fun filterAttributeProcMacros(processor: RsResolveProcessor): RsResolveProcessor =
    processor.wrapWithFilter { e ->
        val function = e.element as? RsFunction ?: return@wrapWithFilter false
        if (!function.isAttributeProcMacroDef) return@wrapWithFilter false
        true
    }

fun filterDeriveProcMacros(processor: RsResolveProcessor): RsResolveProcessor =
    processor.wrapWithFilter { e ->
        val function = e.element as? RsFunction ?: return@wrapWithFilter false
        if (!function.isCustomDeriveProcMacroDef) return@wrapWithFilter false
        true
    }

fun RsOuterAttributeOwner.shouldHideElementInCompletion(context: RsElement, contextMod: RsMod): Boolean {
    val elementContainingMod = containingMod
    if (elementContainingMod == contextMod) return false

    // Hide `#[doc(hidden)]` items
    if (queryAttributes.isDocHidden) {
        val isDeriveMacroInImport = context.ancestorStrict<RsUseItem>() != null
            && this is RsFunction && isCustomDeriveProcMacroDef
            && containingCrate != contextMod.containingCrate
        if (!isDeriveMacroInImport) return true
    }

    val rustcChannel = contextMod.cargoProject?.rustcInfo?.version?.channel ?: return false
    val showUnstableItems = rustcChannel != RustChannel.STABLE && rustcChannel != RustChannel.BETA
    if (showUnstableItems) return false
    if (containingCrate.origin != PackageOrigin.STDLIB) return false

    // Hide unstable stdlib items
    return stability != RsStability.Stable
}

private val RsOuterAttributeOwner.stability: RsStability
    get() {
        // Own stability
        queryAttributes.stability?.let { return it }

        if (this is RsAbstractable) {
            val owner = owner
            if (owner is RsAbstractableOwner.Impl) {
                val ownerStability = owner.impl.queryAttributes.stability
                if (ownerStability != RsStability.Stable) {
                    return ownerStability ?: RsStability.Unstable
                }
                val superItem = superItem
                if (superItem != null) {
                    return superItem.queryAttributes.stability ?: RsStability.Unstable
                }
            }
        }

        // The absence of `#[stable]` attribute in stdlib means the item is unstable
        return RsStability.Unstable
    }
