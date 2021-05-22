/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapiext.isUnitTestMode
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.psi.RsMacroBody
import org.rust.lang.core.psi.RsProcMacroKind
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.body
import org.rust.lang.core.psi.rustFile
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve2.ImportType.GLOB
import org.rust.lang.core.resolve2.ImportType.NAMED
import org.rust.lang.core.resolve2.PartialResolvedImport.*
import org.rust.lang.core.resolve2.util.createDollarCrateHelper
import org.rust.openapiext.findFileByMaybeRelativePath
import org.rust.openapiext.pathAsPath
import org.rust.openapiext.testAssert
import org.rust.openapiext.toPsiFile
import org.rust.stdext.HashCode

private const val CONSIDER_INDETERMINATE_IMPORTS_AS_RESOLVED: Boolean = false

/** Resolves all imports and expands macros (new items are added to [defMap]) using fixed point iteration algorithm */
class DefCollector(
    private val project: Project,
    private val defMap: CrateDefMap,
    private val context: CollectorContext,
) {

    private data class GlobImportInfo(val containingMod: ModData, val visibility: Visibility)

    /**
     * Reversed glob-imports graph, that is
     * for each module (`targetMod`) store all modules which contain glob import to `targetMod`
     */
    private val globImports: MutableMap<ModData /* target mod */, MutableList<GlobImportInfo>> = hashMapOf()
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

            resolveImports()
            val changed = expandMacros()
        } while (changed)
    }

    /**
     * Import resolution
     *
     * This is a fixed point algorithm. We resolve imports until no forward progress in resolving imports is made
     */
    private fun resolveImports() {
        do {
            var hasChangedIndeterminateImports = false
            val hasResolvedImports = unresolvedImports.inPlaceRemoveIf { import ->
                ProgressManager.checkCanceled()
                when (val status = resolveImport(import)) {
                    is Indeterminate -> {
                        if (import.status is Indeterminate && import.status == status) return@inPlaceRemoveIf false

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

                // record the glob import in case we add further items
                val globImports = globImports.computeIfAbsent(targetMod) { mutableListOf() }
                // TODO: If there are two glob imports, we should choose with widest visibility
                if (globImports.none { (mod, _) -> mod == containingMod }) {
                    globImports += GlobImportInfo(containingMod, import.visibility)
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
        if (import.isExternCrate && containingMod.isCrateRoot && name != "_") {
            val types = def.types.singleOrNull() ?: error("null PerNs.types for extern crate import")
            val externCrateDefMap = defMap.getDefMap(types.path.crate)
            externCrateDefMap?.let { defMap.externPrelude[name] = it }
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

        var changed = false
        for ((name, def) in resolutions) {
            val changedCurrent = if (name != "_") {
                val defAdjusted = def.adjust(visibility, isFromNamedImport = importType == NAMED)
                    .adjustMultiresolve()
                pushResolutionFromImport(modData, name, defAdjusted)
            } else {
                // TODO: What if `def` is not trait?
                pushTraitResolutionFromImport(modData, def, visibility)
            }

            if (changedCurrent) changed = true
        }
        if (!changed) return changed

        val globImports = globImports[modData] ?: return changed
        for ((globImportingMod, globImportVis) in globImports) {
            // we know all resolutions have the same `visibility`, so we just need to check that once
            if (!visibility.isVisibleFromMod(globImportingMod)) continue
            updateRecursive(globImportingMod, resolutions, globImportVis, GLOB, depth + 1)
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

    private fun expandMacros(): Boolean {
        if (!shouldExpandMacros) return false
        return macroCallsToExpand.inPlaceRemoveIf { call ->
            ProgressManager.checkCanceled()
            // TODO: Actually resolve macro instead of name check
            if (call.path.last() == "include") {
                expandIncludeMacroCall(call)
                return@inPlaceRemoveIf true
            }
            tryExpandMacroCall(call)
        }
    }

    private fun tryExpandMacroCall(call: MacroCallInfo): Boolean {
        val def = defMap.resolveMacroCallToMacroDefInfo(call.containingMod, call.path, call.macroIndex)
            ?: return false

        if (def is ProcMacroDefInfo && def.isHardcodedNotAMacro && call.originalItem != null) {
            val (visItem, namespaces) = call.originalItem
            call.containingMod.addVisibleItem(visItem.name, PerNs.from(visItem, namespaces))
            return true
        }

        val defData = RsMacroDataWithHash.fromDefInfo(def)
            ?: return false
        val callData = RsMacroCallDataWithHash(RsMacroCallData(call.body, defMap.metaData.env), call.bodyHash)
        val (expandedFile, expansion) =
            macroExpanderShared.createExpansionStub(project, macroExpander, defData, callData) ?: return true

        val dollarCrateHelper = if (def is DeclMacroDefInfo) {
            createDollarCrateHelper(call, def, expansion)
        } else {
            null
        }

        val context = getModCollectorContextForExpandedElements(call) ?: return true
        collectExpandedElements(expandedFile, call, context, dollarCrateHelper)
        return true
    }

    private fun expandIncludeMacroCall(call: MacroCallInfo) {
        val modData = call.containingMod
        val containingFile = PersistentFS.getInstance().findFileById(modData.fileId) ?: return
        val includePath = (call.body as? MacroCallBody.FunctionLike)?.text ?: return
        val parentDirectory = containingFile.parent
        val includingFile = parentDirectory
            .findFileByMaybeRelativePath(includePath)
            ?.toPsiFile(project)
            ?.rustFile
        if (includingFile != null) {
            val context = getModCollectorContextForExpandedElements(call) ?: return
            collectFileAndCalculateHash(includingFile, call.containingMod, call.macroIndex, context)
        } else {
            val filePath = parentDirectory.pathAsPath.resolve(includePath)
            defMap.missedFiles.add(filePath)
        }
    }

    private fun getModCollectorContextForExpandedElements(call: MacroCallInfo): ModCollectorContext? {
        if (call.depth >= DEFAULT_RECURSION_LIMIT) return null
        return ModCollectorContext(
            defMap = defMap,
            crateRoot = defMap.root,
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
) : MacroDefInfo()

class ProcMacroDefInfo(
    override val crate: CratePersistentId,
    override val path: ModPath,
    val procMacroKind: RsProcMacroKind,
    val procMacroArtifact: CargoWorkspaceData.ProcMacroArtifact?,
    val isHardcodedNotAMacro: Boolean,
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
    val dollarCrateMap: RangeMap = RangeMap.EMPTY,
    val originalItem: Pair<VisItem, Set<Namespace>>? = null,
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

    val allMods = hashSetOf<ModData>()
    collectChildMods(defMap.root, allMods)
    context.imports.removeIf { it.containingMod !in allMods }
    context.macroCalls.removeIf { it.containingMod !in allMods }
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
