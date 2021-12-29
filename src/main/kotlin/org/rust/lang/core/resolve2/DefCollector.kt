/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.body
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve2.ImportType.GLOB
import org.rust.lang.core.resolve2.ImportType.NAMED
import org.rust.lang.core.resolve2.PartialResolvedImport.*
import org.rust.lang.core.resolve2.util.DollarCrateMap
import org.rust.lang.core.resolve2.util.createDollarCrateHelper
import org.rust.lang.core.stubs.RsFileStub
import org.rust.openapiext.*
import org.rust.stdext.HashCode
import org.rust.stdext.getWithRethrow
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import kotlin.math.ceil

private const val CONSIDER_INDETERMINATE_IMPORTS_AS_RESOLVED: Boolean = false
private val EXPAND_MACROS_IN_PARALLEL: RegistryValue = Registry.get("org.rust.resolve.new.engine.macros.parallel")

/** Resolves all imports and expands macros (new items are added to [defMap]) using fixed point iteration algorithm */
class DefCollector(
    private val project: Project,
    private val defMap: CrateDefMap,
    private val context: CollectorContext,
    private val pool: ExecutorService?,
    private val indicator: ProgressIndicator,
) {

    /**
     * Reversed glob-imports graph, that is
     * for each module (`targetMod`) store all modules which contain glob import to `targetMod`
     */
    private val globImports: MutableMap<
        ModData /* target mod */,
        MutableMap<ModData /* source mod */, Visibility>
        > = hashMapOf()
    private val unresolvedImports: MutableList<Import> = context.imports
    private val resolvedImports: MutableList<Import> = mutableListOf()

    private val macroCallsToExpand: MutableList<MacroCallInfo> = context.macroCalls

    /** Created once as optimization */
    private val macroExpander = FunctionLikeMacroExpander.new(project)
    private val macroExpanderShared: MacroExpansionSharedCache = MacroExpansionSharedCache.getInstance()

    private val shouldExpandMacros: Boolean =
        when (val mode = project.macroExpansionManager.macroExpansionMode) {
            MacroExpansionMode.Disabled -> false
            is MacroExpansionMode.New -> mode.scope != MacroExpansionScope.NONE
            MacroExpansionMode.Old -> true
        }

    fun collect() {
        do {
            // Have to call it in loop, because macro can expand to
            // two cfg-disabled mods with same name (first one will be shadowed).
            // See [RsCfgAttrResolveTest.`test import inside expanded shadowed mod 1`].
            removeInvalidImportsAndMacroCalls(defMap, context)
            sortImports(unresolvedImports)

            resolveImports()
            val changed = expandMacros()
        } while (changed)
        if (!context.isHangingMode) {
            defMap.afterBuilt()
        }
    }

    /**
     * Import resolution
     *
     * This is a fixed point algorithm. We resolve imports until no forward progress in resolving imports is made
     */
    private fun resolveImports() {
        do {
            var hasChangedIndeterminateImports = false
            val hasResolvedImports = unresolvedImports.removeIf { import ->
                ProgressManager.checkCanceled()
                when (val status = resolveImport(import)) {
                    is Indeterminate -> {
                        if (import.status is Indeterminate && import.status == status) return@removeIf false

                        import.status = status
                        val changed = recordResolvedImport(import)
                        if (changed) hasChangedIndeterminateImports = true
                        if (CONSIDER_INDETERMINATE_IMPORTS_AS_RESOLVED) {
                            resolvedImports.add(import)
                            true
                        } else {
                            false
                        }
                    }
                    is Resolved -> {
                        import.status = status
                        recordResolvedImport(import)
                        resolvedImports.add(import)
                        true
                    }
                    Unresolved -> false
                }
            }
        } while (hasResolvedImports || hasChangedIndeterminateImports)
    }

    private fun resolveImport(import: Import): PartialResolvedImport {
        if (import.isExternCrate) {
            val externCrateName = import.usePath.single()
            val externCrateDefMap = defMap.resolveExternCrateAsDefMap(externCrateName) ?: return Unresolved
            val def = externCrateDefMap.rootAsPerNs.adjust(import.visibility, isFromNamedImport = true)
            return Resolved(def)
        }

        val result = defMap.resolvePathFp(
            import.containingMod,
            import.usePath,
            ResolveMode.IMPORT,
            withInvisibleItems = import.visibility.isInvisible
        )
        val perNs = result.resolvedDef

        if (!result.reachedFixedPoint || perNs.isEmpty) return Unresolved

        // for path `mod1::mod2::mod3::foo`
        // if any of `mod1`, ... , `mod3` is from other crate
        // then it means that defMap for that crate is already completely filled
        if (result.visitedOtherCrate) return Resolved(perNs)

        val isResolvedInAllNamespaces = perNs.hasAllNamespaces
        val isResolvedGlobImport = import.isGlob && perNs.types.isNotEmpty()
        return if (isResolvedInAllNamespaces || isResolvedGlobImport) {
            Resolved(perNs)
        } else {
            Indeterminate(perNs)
        }
    }

    private fun recordResolvedImport(import: Import): Boolean {
        val def = when (val status = import.status) {
            is Resolved -> status.perNs
            is Indeterminate -> status.perNs
            Unresolved -> error("expected resoled import")
        }

        return if (import.isGlob) {
            recordResolvedGlobImport(import, def)
        } else {
            recordResolvedNamedImport(import, def)
        }
    }

    private fun recordResolvedGlobImport(import: Import, def: PerNs): Boolean {
        val types = def.types.singleOrNull { it.isModOrEnum } ?: run {
            if (isUnitTestMode) error("Glob import from not module or enum: $import")
            return false
        }
        val targetMod = defMap.tryCastToModData(types) ?: return false
        val containingMod = import.containingMod
        when {
            import.isPrelude -> {
                defMap.prelude = targetMod
                return true
            }
            targetMod.crate == defMap.crate -> {
                // glob import from same crate => we do an initial import,
                // and then need to propagate any further additions
                val items = targetMod.getVisibleItems { it.isVisibleFromMod(containingMod) }
                val changed = update(containingMod, items, import.visibility, GLOB)

                if (!context.isHangingMode) {
                    /** See [CrateDefMap.hasTransitiveGlobImport] */
                    defMap.globImportGraph.recordGlobImport(containingMod, targetMod, import.visibility)
                }

                // record the glob import in case we add further items
                val globImports = globImports.computeIfAbsent(targetMod) { hashMapOf() }
                val existingGlobImportVisibility = globImports[containingMod]
                if (existingGlobImportVisibility?.isStrictlyMorePermissive(import.visibility) != true) {
                    globImports[containingMod] = import.visibility
                }
                return changed
            }
            else -> {
                // glob import from other crate => we can just import everything once
                val items = targetMod.getVisibleItems { it.isVisibleFromOtherCrate() }
                return update(containingMod, items, import.visibility, GLOB)
            }
        }
    }

    private fun recordResolvedNamedImport(import: Import, def: PerNs): Boolean {
        val containingMod = import.containingMod
        val name = import.nameInScope

        // extern crates in the crate root are special-cased to insert entries into the extern prelude
        // https://github.com/rust-lang/rust/pull/54658
        if (import.isExternCrate && containingMod.isCrateRoot && name != "_" && !context.isHangingMode) {
            val types = def.types.singleOrNull() ?: error("null PerNs.types for extern crate import")
            val externCrateDefMap = defMap.getDefMap(types.path.crate)
            externCrateDefMap?.let {
                defMap.externPrelude[name] = it
                defMap.externCratesInRoot[name] = it
            }
        }

        val defWithAdjustedVisible = def.mapItems {
            if (it.visibility.isInvisible || it.visibility.isVisibleFromMod(containingMod)) {
                it
            } else {
                it.copy(visibility = Visibility.Invisible)
            }
        }
        return update(containingMod, listOf(name to defWithAdjustedVisible), import.visibility, NAMED)
    }

    /**
     * [resolutions] were added (imported or expanded from macro) to [modData] with [visibility].
     * We update [ModData.visibleItems] and propagate [resolutions] to modules which have glob import from [modData].
     */
    private fun update(
        modData: ModData,
        resolutions: List<Pair<String, PerNs>>,
        visibility: Visibility,
        importType: ImportType
    ): Boolean = updateRecursive(modData, resolutions, visibility, importType, depth = 0)

    private fun updateRecursive(
        modData: ModData,
        resolutions: List<Pair<String, PerNs>>,
        // All resolutions are imported with this visibility,
        // the visibilities in the `PerNs` values are ignored and overwritten
        visibility: Visibility,
        importType: ImportType,
        depth: Int
    ): Boolean {
        check(depth <= 100) { "infinite recursion in glob imports!" }

        val resolutionsNew = resolutions.filter { (name, def) ->
            if (name != "_") {
                val defAdjusted = def.adjust(visibility, isFromNamedImport = importType == NAMED)
                    .adjustMultiresolve()
                pushResolutionFromImport(modData, name, defAdjusted)
            } else {
                // TODO: What if `def` is not trait?
                pushTraitResolutionFromImport(modData, def, visibility)
            }
        }
        val changed = resolutionsNew.isNotEmpty()
        if (!changed) return changed

        val globImports = globImports[modData] ?: return changed
        for ((globImportingMod, globImportVis) in globImports) {
            // we know all resolutions have the same `visibility`, so we just need to check that once
            if (!visibility.isVisibleFromMod(globImportingMod)) continue
            updateRecursive(globImportingMod, resolutionsNew, globImportVis, GLOB, depth + 1)
        }
        return changed
    }

    private fun pushTraitResolutionFromImport(modData: ModData, def: PerNs, visibility: Visibility): Boolean {
        check(!def.isEmpty)
        var changed = false
        for (trait in def.types) {
            if (trait.isModOrEnum) continue
            val oldVisibility = modData.unnamedTraitImports[trait.path]
            if (oldVisibility == null || visibility.isStrictlyMorePermissive(oldVisibility)) {
                modData.unnamedTraitImports[trait.path] = visibility
                changed = true
            }
        }
        return changed
    }

    private data class ExpansionInput(val call: MacroCallInfo, val def: MacroDefInfo)
    private data class ExpansionOutput(
        val call: MacroCallInfo,
        val def: MacroDefInfo,
        val expandedFile: RsFileStub,
        val expansion: ExpansionResultOk
    )

    private fun expandMacros(): Boolean {
        if (!shouldExpandMacros) return false
        val macrosToExpandInParallel = mutableListOf<ExpansionInput>()
        val changed = macroCallsToExpand.inPlaceRemoveIf { call ->
            ProgressManager.checkCanceled()
            // TODO: Actually resolve macro instead of name check
            if (call.path.last() == "include") {
                expandIncludeMacroCall(call)
                return@inPlaceRemoveIf true
            }

            val def = defMap.resolveMacroCallToMacroDefInfo(call.containingMod, call.path, call.macroIndex)
                ?: return@inPlaceRemoveIf false

            if (tryTreatAsIdentityMacro(call, def)) return@inPlaceRemoveIf true

            macrosToExpandInParallel += ExpansionInput(call, def)
            true
        }
        expandMacrosInParallel(macrosToExpandInParallel)
        return changed
    }

    private fun tryTreatAsIdentityMacro(call: MacroCallInfo, def: MacroDefInfo): Boolean {
        if (def !is ProcMacroDefInfo || !def.kind.treatAsBuiltinAttr || call.originalItem == null) return false
        val (visItem, namespaces, procMacroKind) = call.originalItem

        /** See also [ModCollector.collectSimpleItem] */
        call.containingMod.addVisibleItem(visItem.name, PerNs.from(visItem, namespaces))
        if (procMacroKind != null) {
            call.containingMod.procMacros[visItem.name] = procMacroKind
        }
        return true
    }

    private fun expandMacrosInParallel(macros: List<ExpansionInput>) {
        if (macros.isEmpty()) return
        val batches = macros.splitInBatches(100)

        val result = if (pool != null && EXPAND_MACROS_IN_PARALLEL.asBoolean()) {
            val indicator = indicator.toThreadSafeProgressIndicator()
            // Don't use `.parallelStream()` - for typical count of batches (10-20) it will run all tasks on current thread
            val tasks = batches.map { batch ->
                Callable {
                    computeInReadActionWithWriteActionPriority(SensitiveProgressWrapper(indicator)) {
                        expandMacrosInBatch(batch)
                    }
                }
            }
            pool.invokeAll(tasks).map { it.getWithRethrow() }
        } else {
            batches.map { expandMacrosInBatch(it) }
        }
        for (batch in result) {
            batch.forEach(this::recordExpansion)
        }
    }

    private fun expandMacrosInBatch(batch: List<ExpansionInput>): List<ExpansionOutput> =
        batch.mapNotNull { (call, def) -> expandMacro(call, def) }

    private fun expandMacro(call: MacroCallInfo, def: MacroDefInfo): ExpansionOutput? {
        val defData = RsMacroDataWithHash.fromDefInfo(def).ok() ?: return null
        val callData = RsMacroCallDataWithHash(RsMacroCallData(call.body, defMap.metaData.env), call.bodyHash)
        val (expandedFile, expansion) =
            macroExpanderShared.createExpansionStub(project, macroExpander, defData, callData) ?: return null
        return ExpansionOutput(call, def, expandedFile, expansion)
    }

    private fun recordExpansion(result: ExpansionOutput): Boolean {
        val (call, def, expandedFile, expansion) = result
        val dollarCrateHelper = createDollarCrateHelper(call, def, expansion)

        val context = getModCollectorContextForExpandedElements(call) ?: return true
        collectExpandedElements(expandedFile, call, context, dollarCrateHelper)
        return true
    }

    private fun expandIncludeMacroCall(call: MacroCallInfo) {
        val modData = call.containingMod
        val containingFile = PersistentFS.getInstance().findFileById(modData.fileId ?: return) ?: return
        val includePath = (call.body as? MacroCallBody.FunctionLike)?.text ?: return
        val parentDirectory = containingFile.parent
        val includingFile = parentDirectory.findFileByMaybeRelativePath(includePath)
        val includingRsFile = includingFile?.toPsiFile(project)?.rustFile
        if (includingRsFile != null) {
            val context = getModCollectorContextForExpandedElements(call) ?: return
            collectScope(includingRsFile, call.containingMod, context, call.macroIndex)
        } else if (!context.isHangingMode) {
            val filePath = parentDirectory.pathAsPath.resolve(includePath)
            defMap.missedFiles.add(filePath)
        }
        if (includingFile != null) {
            modData.recordChildFileInUnusualLocation(includingFile.fileId)
        }
    }

    private fun getModCollectorContextForExpandedElements(call: MacroCallInfo): ModCollectorContext? {
        if (call.depth >= DEFAULT_RECURSION_LIMIT) return null
        return ModCollectorContext(
            defMap = defMap,
            context = context,
            macroDepth = call.depth + 1,
            onAddItem = ::onAddItem
        )
    }

    private fun onAddItem(modData: ModData, name: String, perNs: PerNs, visibility: Visibility): Boolean {
        return update(modData, listOf(name to perNs), visibility, NAMED)
    }
}

class Import(
    val containingMod: ModData,
    val usePath: Array<String>,
    val nameInScope: String,
    val visibility: Visibility,
    val isGlob: Boolean = false,
    val isExternCrate: Boolean = false,
    val isPrelude: Boolean = false,  // #[prelude_import]
) {
    var status: PartialResolvedImport = Unresolved

    override fun toString(): String {
        val visibility = when (visibility) {
            Visibility.Public -> "pub "
            is Visibility.Restricted -> when {
                visibility.inMod == containingMod -> ""
                visibility.inMod.isCrateRoot -> "pub(crate) "
                else -> "pub(in ${visibility.inMod}) "
            }
            Visibility.Invisible -> "invisible "
            Visibility.CfgDisabled -> "#[cfg(false)] "
        }
        val use = if (isExternCrate) "extern crate" else "use"
        val glob = if (isGlob) "::*" else ""
        val alias = if (usePath.last() == nameInScope || isGlob) "" else " as $nameInScope"
        return "`${visibility}$use ${usePath.joinToString("::")}$glob$alias;` in ${containingMod.path}"
    }
}

enum class ImportType { NAMED, GLOB }

sealed class PartialResolvedImport {
    /** None of any namespaces is resolved */
    object Unresolved : PartialResolvedImport()

    /** One of namespaces is resolved */
    data class Indeterminate(val perNs: PerNs) : PartialResolvedImport()

    /** All namespaces are resolved, OR it is came from other crate */
    data class Resolved(val perNs: PerNs) : PartialResolvedImport()
}

sealed class MacroDefInfo {
    abstract val crate: CratePersistentId
    abstract val path: ModPath
}

class DeclMacroDefInfo(
    override val crate: CratePersistentId,
    override val path: ModPath,
    val macroIndex: MacroIndex,
    private val bodyText: String,
    val bodyHash: HashCode,
    val hasMacroExport: Boolean,
    val hasLocalInnerMacros: Boolean,
    val hasRustcBuiltinMacro: Boolean,
    project: Project,
) : MacroDefInfo() {
    /** Lazy because usually it should not be used (thanks to macro expansion cache) */
    val body: Lazy<RsMacroBody?> = lazy(LazyThreadSafetyMode.PUBLICATION) {
        val psiFactory = RsPsiFactory(project, markGenerated = false)
        psiFactory.createMacroBody(bodyText)
    }
}

class DeclMacro2DefInfo(
    override val crate: CratePersistentId,
    override val path: ModPath,
    private val bodyText: String,
    val bodyHash: HashCode,
    val hasRustcBuiltinMacro: Boolean,
    project: Project,
) : MacroDefInfo() {
    /** Lazy because usually it should not be used (thanks to macro expansion cache) */
    val body: Lazy<RsMacroBody?> = lazy(LazyThreadSafetyMode.PUBLICATION) {
        val psiFactory = RsPsiFactory(project, markGenerated = false)
        psiFactory.createMacroBody(bodyText)
    }
}

class ProcMacroDefInfo(
    override val crate: CratePersistentId,
    override val path: ModPath,
    val procMacroKind: RsProcMacroKind,
    val procMacroArtifact: CargoWorkspaceData.ProcMacroArtifact?,
    val kind: KnownProcMacroKind,
) : MacroDefInfo()

class MacroCallInfo(
    val containingMod: ModData,
    val macroIndex: MacroIndex,
    val path: Array<String>,
    val body: MacroCallBody,
    val bodyHash: HashCode?,  // null for `include!` macro
    val depth: Int,
    /**
     * `srcOffset` - [CratePersistentId]
     * `dstOffset` - index of [MACRO_DOLLAR_CRATE_IDENTIFIER] in [body]
     */
    val dollarCrateMap: DollarCrateMap = DollarCrateMap.EMPTY,

    /**
     * Non-null in the case of attribute procedural macro if we can fall back that item
     * ([org.rust.lang.core.psi.RsProcMacroPsiUtil.canFallBackAttrMacroToOriginalItem])
     */
    val originalItem: Triple<VisItem, Set<Namespace>, RsProcMacroKind?>? = null,
) {
    override fun toString(): String = "${containingMod.path}:  ${path.joinToString("::")}! { $body }"
}

/**
 * "Invalid" means it belongs to [ModData] which is no longer accessible from `defMap.root` using [ModData.childModules]
 * It could happen if there is cfg-disabled module, which we collect first (with its imports)
 * And then cfg-enabled module overrides previously created [ModData]
 */
private fun removeInvalidImportsAndMacroCalls(defMap: CrateDefMap, context: CollectorContext) {
    fun collectChildMods(mod: ModData, allMods: HashSet<ModData>) {
        allMods += mod
        for (child in mod.childModules.values) {
            collectChildMods(child, allMods)
        }
    }

    if (context.isHangingMode) return
    val allMods = hashSetOf<ModData>()
    collectChildMods(defMap.root, allMods)
    context.imports.removeIf { it.containingMod !in allMods }
    context.macroCalls.removeIf { it.containingMod !in allMods }
}

/**
 * This is a workaround for some real-project cases. See:
 * - [RsUseResolveTest.`test import adds same name as existing`]
 * - https://github.com/rust-lang/cargo/blob/875e0123259b0b6299903fe4aea0a12ecde9324f/src/cargo/util/mod.rs#L23
 */
private fun sortImports(imports: MutableList<Import>) {
    imports.sortWith(
        compareBy<Import> { it.visibility === Visibility.CfgDisabled }  // cfg-enabled imports first
            .thenBy { it.isGlob }  // named imports first
            .thenByDescending { it.nameInScope in it.containingMod.visibleItems }
            .thenByDescending { it.containingMod.path.segments.size }  // imports from nested modules first
    )
}

/**
 * Faster alternative to [java.util.Collection.removeIf] that doesn't preserve element order.
 * [filter] is allowed to append to [this].
 * return true if any elements were removed.
 */
private inline fun <T> MutableList<T>.inPlaceRemoveIf(filter: (T) -> Boolean): Boolean {
    var removed = false
    var i = 0
    while (i < size) {
        if (filter(this[i])) {
            removed = true
            this[i] = last()
            removeAt(lastIndex)
        } else {
            ++i
        }
    }
    return removed
}

private fun <T> List<T>.splitInBatches(batchSize: Int): List<List<T>> {
    if (size <= batchSize) return listOf(this)
    val numberBatches = ceil(size.toDouble() / batchSize).toInt()
    val adjustedBatchSize = ceil(size.toDouble() / numberBatches).toInt()
    return chunked(adjustedBatchSize)
}

fun pushResolutionFromImport(modData: ModData, name: String, def: PerNs): Boolean {
    check(!def.isEmpty)

    // optimization: fast path
    val defExisting = modData.visibleItems.putIfAbsent(name, def) ?: return true

    return mergeResolutionFromImport(modData, name, def, defExisting)
}

private fun mergeResolutionFromImport(modData: ModData, name: String, def: PerNs, defExisting: PerNs): Boolean {
    val typesNew = mergeResolutionOneNs(def.types, defExisting.types)
    val valuesNew = mergeResolutionOneNs(def.values, defExisting.values)
    val macrosNew = mergeResolutionOneNs(def.macros, defExisting.macros)
    if (defExisting.types.contentEquals(typesNew)
        && defExisting.values.contentEquals(valuesNew)
        && defExisting.macros.contentEquals(macrosNew)
    ) return false
    modData.visibleItems[name] = PerNs(typesNew, valuesNew, macrosNew)
    return true
}

private fun mergeResolutionOneNs(visItems: Array<VisItem>, visItemsExisting: Array<VisItem>): Array<VisItem> {
    if (visItems.isEmpty()) return visItemsExisting
    if (visItemsExisting.isEmpty()) return visItems

    // all existing items must have same visibility type
    val visibilityType = visItems.visibilityType()
    val visibilityTypeExisting = visItemsExisting.visibilityType()
    if (visibilityType.isWider(visibilityTypeExisting)) return visItems
    if (visibilityTypeExisting.isWider(visibilityType)) return visItemsExisting

    // all existing items must have same import type
    val importType = visItems.importType()
    val importTypeExisting = visItemsExisting.importType()
    if (importType == GLOB && importTypeExisting == NAMED) return visItemsExisting
    if (importType == NAMED && importTypeExisting == GLOB) return visItems

    // for performance reasons
    if (visibilityTypeExisting == VisibilityType.CfgDisabled && visibilityType == VisibilityType.CfgDisabled) {
        return visItems
    }

    // actual multiresolve - unite items and if there is two same items (same path) choose widest visibility
    return mergeResolutionOneNsMultiresolve(visItems, visItemsExisting)
}

private fun mergeResolutionOneNsMultiresolve(
    visItems: Array<VisItem>,
    visItemsExisting: Array<VisItem>
): Array<VisItem> {
    val result = visItemsExisting.associateByTo(hashMapOf()) { it.path }
    for (visItem in visItems) {
        val visItemExisting = result.putIfAbsent(visItem.path, visItem) ?: continue
        if (visItem.visibility.isStrictlyMorePermissive(visItemExisting.visibility)) {
            result[visItem.path] = visItem
        }
        // else keep [visItemExisting]
    }
    return result.values.toTypedArray()
}

// all items must have same visibility type
fun Array<VisItem>.visibilityType(): VisibilityType {
    val visibilityType = first().visibility.type
    testAssert { all { it.visibility.type == visibilityType } }
    return visibilityType
}

private fun Array<VisItem>.importType(): ImportType {
    val isFromNamedImport = first().isFromNamedImport
    testAssert { all { it.isFromNamedImport == isFromNamedImport } }
    return if (isFromNamedImport) NAMED else GLOB
}

private fun CrateDefMap.afterBuilt() {
    root.visitDescendants {
        it.isShadowedByOtherFile = false
    }

    // TODO: uncomment when #[cfg_attr] will be supported
    // testAssert { missedFiles.isEmpty() }
}
