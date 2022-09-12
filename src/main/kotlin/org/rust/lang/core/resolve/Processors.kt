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
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.hasTyInfer
import org.rust.lang.core.types.ty.*

/**
 * ScopeEntry is some PsiElement visible in some code scope.
 *
 * [ScopeEntry] handles the two case:
 *   * aliases (that's why we need a [name] property)
 *   * lazy resolving of actual elements (that's why [element] can return `null`)
 */
interface ScopeEntry {
    val name: String
    val element: RsElement?
    val subst: Substitution get() = emptySubstitution
}

typealias RsProcessor<T> = (T) -> Boolean

interface RsResolveProcessorBase<in T : ScopeEntry> {
    /**
     * Return `true` to stop further processing,
     * return `false` to continue search
     */
    operator fun invoke(entry: T): Boolean

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
        override fun invoke(entry: T): Boolean = processor(entry)
        override val names: Set<String>? = names
        override fun toString(): String = "Processor(name=$names)"
    }
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
    val element = e.element ?: return
    if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
        val visibilityStatus = e.getVisibilityStatusFrom(ctx.context)
        if (visibilityStatus != VisibilityStatus.CfgDisabled) {
            val isVisible = visibilityStatus == VisibilityStatus.Visible
            result += RsPathResolveResult(element, e.subst.foldTyInferWithTyPlaceholder(), isVisible)
        }
    }
}
// This is basically a hack - we replace type variables incorrectly created during name resolution
// TODO don't create `TyInfer.TyVar` during name resolution
private fun Substitution.foldTyInferWithTyPlaceholder(): Substitution =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty {
            val foldedTy = if (ty is TyInfer.TyVar) {
                if (ty.origin is RsBaseType) {
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
            val element = e.element ?: return@createProcessor false
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
            // de-lazying. See `RsResolveProcessor.lazy`
            val element = e.element ?: return@createProcessorGeneric false
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
            if (element != null && (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf)) {
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
        val element = e.element ?: return@createProcessor false

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
    override val subst: Substitution = emptySubstitution
) : ScopeEntry

data class ScopeEntryWithVisibility(
    override val name: String,
    override val element: RsElement,
    /** Given a [RsElement] (usually [RsPath]) checks if this item is visible in `containingMod` of that element */
    val visibilityFilter: (RsElement) -> VisibilityStatus,
    override val subst: Substitution = emptySubstitution,
) : ScopeEntry

fun ScopeEntry.getVisibilityStatusFrom(context: RsElement): VisibilityStatus =
    if (this is ScopeEntryWithVisibility) {
        visibilityFilter(context)
    } else {
        VisibilityStatus.Visible
    }

fun ScopeEntry.isVisibleFrom(context: RsElement): Boolean =
    getVisibilityStatusFrom(context) == VisibilityStatus.Visible

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
    override val subst: Substitution = emptySubstitution,
    override val selfTy: Ty,
    override val source: TraitImplSource
) : AssocItemScopeEntryBase<RsAbstractable>


operator fun RsResolveProcessor.invoke(name: String, e: RsElement): Boolean =
    this(SimpleScopeEntry(name, e))

operator fun RsResolveProcessor.invoke(
    name: String,
    e: RsElement,
    visibilityFilter: (RsElement) -> VisibilityStatus
): Boolean = this(ScopeEntryWithVisibility(name, e, visibilityFilter))

inline fun RsResolveProcessor.lazy(name: String, e: () -> RsElement?): Boolean {
    if (!acceptsName(name)) return false
    val element = e() ?: return false
    return this(name, element)
}

operator fun RsResolveProcessor.invoke(e: RsNamedElement): Boolean {
    val name = e.name ?: return false
    return this(name, e)
}

operator fun RsResolveProcessor.invoke(e: BoundElement<RsNamedElement>): Boolean {
    val name = e.element.name ?: return false
    return this(SimpleScopeEntry(name, e.element, e.subst))
}

fun processAll(elements: List<RsNamedElement>, processor: RsResolveProcessor): Boolean {
    return elements.any { processor(it) }
}

fun processAllScopeEntries(elements: List<ScopeEntry>, processor: RsResolveProcessor): Boolean {
    return elements.any { processor(it) }
}

fun processAllWithSubst(
    elements: Collection<RsNamedElement>,
    subst: Substitution,
    processor: RsResolveProcessor
): Boolean {
    for (e in elements) {
        if (processor(BoundElement(e, subst))) return true
    }
    return false
}

fun filterNotCfgDisabledItemsAndTestFunctions(processor: RsResolveProcessor): RsResolveProcessor {
    return createProcessor(processor.names) { e ->
        val element = e.element ?: return@createProcessor false
        if (element is RsFunction && element.isTest) return@createProcessor false
        if (element is RsDocAndAttributeOwner && !element.existsAfterExpansionSelf) return@createProcessor false

        processor(e)
    }
}

fun filterCompletionVariantsByVisibility(context: RsElement, processor: RsResolveProcessor): RsResolveProcessor {
    // Do not filter out private items in debugger
    if (context.containingFile is RsDebuggerExpressionCodeFragment) {
        return processor
    }
    val mod = context.containingMod
    return createProcessor(processor.names) {
        val element = it.element
        if (element is RsVisible && !element.isVisibleFrom(mod)) return@createProcessor false
        if (!it.isVisibleFrom(context)) return@createProcessor false

        val isHidden = element is RsOuterAttributeOwner && element.queryAttributes.isDocHidden &&
            element.containingMod != mod
        if (isHidden) return@createProcessor false

        processor(it)
    }
}

fun filterAttributeProcMacros(processor: RsResolveProcessor): RsResolveProcessor =
    createProcessor(processor.names) { e ->
        val function = e.element as? RsFunction ?: return@createProcessor false
        if (!function.isAttributeProcMacroDef) return@createProcessor false
        processor(e)
    }
