/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.util.SmartList
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

fun createProcessor(name: String? = null, processor: (ScopeEntry) -> Boolean): RsResolveProcessor =
    createProcessorGeneric(name, processor)

fun createProcessor(names: Set<String>?, processor: (ScopeEntry) -> Boolean): RsResolveProcessor =
    createProcessorGeneric(names, processor)

fun <T : ScopeEntry> createProcessorGeneric(
    name: String? = null,
    processor: (T) -> Boolean
): RsResolveProcessorBase<T> = createProcessorGeneric(name?.let { setOf(it) }, processor)

fun <T : ScopeEntry> createProcessorGeneric(
    names: Set<String>? = null,
    processor: (T) -> Boolean
): RsResolveProcessorBase<T> {
    return object : RsResolveProcessorBase<T> {
        override fun process(entry: T): Boolean = processor(entry)
        override val names: Set<String>? = names
        override fun toString(): String = "Processor(names=$names)"
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
    val result = SmartList<RsPathResolveResult<RsElement>>()
    val processor = createProcessor(referenceName) { e ->
        if (e.name == referenceName) {
            collectPathScopeEntry(ctx, result, e)
        }
        false
    }
    f(processor)
    return result
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
    val processor = createProcessor(resultByName.keys) { e ->
        val list = resultByName[e.name]
        if (list != null) {
            collectPathScopeEntry(ctx, list, e)
        }
        false
    }
    f(processor)
    return result
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
    val result = SmartList<RsElement>()
    val processor = createProcessor(referenceName) { e ->
        if (e.name == referenceName) {
            val element = e.element
            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
                result += element
            }
        }
        false
    }
    f(processor)
    return result
}

fun <T : ScopeEntry> collectResolveVariantsAsScopeEntries(
    referenceName: String?,
    f: (RsResolveProcessorBase<T>) -> Unit
): List<T> {
    if (referenceName == null) return emptyList()
    val result = mutableListOf<T>()
    val processor = createProcessorGeneric<T>(referenceName) { e ->
        if (e.name == referenceName) {
            val element = e.element
            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
                result += e
            }
        }
        false
    }
    f(processor)
    return result
}

fun pickFirstResolveVariant(referenceName: String?, f: (RsResolveProcessor) -> Unit): RsElement? =
    pickFirstResolveEntry(referenceName, f)?.element

fun pickFirstResolveEntry(referenceName: String?, f: (RsResolveProcessor) -> Unit): ScopeEntry? {
    if (referenceName == null) return null
    var result: ScopeEntry? = null
    val processor = createProcessor(referenceName) { e ->
        if (e.name == referenceName) {
            val element = e.element
            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
                result = e
                return@createProcessor true
            }
        }
        false
    }
    f(processor)
    return result
}

fun collectCompletionVariants(
    result: CompletionResultSet,
    context: RsCompletionContext,
    f: (RsResolveProcessor) -> Unit
) {
    val processor = createProcessor { e ->
        val element = e.element

        if (element is RsEnumItem
            && (context.expectedTy?.ty?.stripReferences() as? TyAdt)?.item == (element.declaredType as? TyAdt)?.item) {
            val variants = collectVariantsForEnumCompletion(element, context, e.subst)
            result.addAllElements(variants)
        }

        result.addElement(createLookupElement(
            scopeEntry = e,
            context = context
        ))

        false
    }
    f(processor)
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
    val mod = context.containingMod
    return processor.wrapWithFilter {
        val element = it.element
        if (element is RsVisible && !element.isVisibleFrom(mod)) return@wrapWithFilter false
        if (!it.isVisibleFrom(context)) return@wrapWithFilter false

        val isHidden = element is RsOuterAttributeOwner && element.queryAttributes.isDocHidden &&
            element.containingMod != mod
        if (isHidden) return@wrapWithFilter false

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
