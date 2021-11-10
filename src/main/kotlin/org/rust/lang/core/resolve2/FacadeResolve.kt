/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import org.jetbrains.annotations.VisibleForTesting
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.refactoring.move.common.RsMoveUtil.containingModOrSelf
import org.rust.lang.core.completion.RsMacroCompletionProvider
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.crate.impl.CargoBasedCrate
import org.rust.lang.core.crate.impl.DoctestCrate
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.macros.errors.ResolveMacroWithoutPsiError
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS
import org.rust.lang.core.resolve.ref.RsMacroPathReferenceImpl
import org.rust.lang.core.resolve.ref.RsResolveCache
import org.rust.lang.core.resolve2.RsModInfoBase.*
import org.rust.openapiext.toPsiFile
import org.rust.stdext.RsResult

val IS_NEW_RESOLVE_ENABLED_KEY: RegistryValue = Registry.get("org.rust.resolve.new.engine")
val Project.isNewResolveEnabled: Boolean
    get() = IS_NEW_RESOLVE_ENABLED_KEY.asBoolean()

/** null return value means that new resolve can't be used */
fun processItemDeclarations2(
    scope: RsItemsOwner,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    ipm: ItemProcessingMode
): Boolean? {
    val info = getModInfo(scope)
    val (_, defMap, modData) = when (info) {
        is CantUseNewResolve -> return null
        InfoNotFound -> return false
        is RsModInfo -> info
    }

    for ((name, perNs) in modData.visibleItems.entriesWithName(processor.name)) {
        // We need a `Set` here because item could belong to multiple namespaces (e.g. unit struct)
        // Also we need to distinguish unit struct and e.g. mod and function with same name in one module
        val elements = hashSetOf<RsNamedElement>()
        for ((visItems, namespace) in perNs.getVisItemsByNamespace()) {
            if (namespace !in ns) continue
            for (visItem in visItems) {
                if (ipm == WITHOUT_PRIVATE_IMPORTS && visItem.visibility == Visibility.Invisible) continue
                if (namespace == Namespace.Types && visItem.visibility.isInvisible && name in defMap.externPrelude) continue
                val visibilityFilter = visItem.visibility.createFilter(info)
                for (element in visItem.toPsi(info, namespace)) {
                    if (!elements.add(element)) continue
                    if (processor(name, element, visibilityFilter)) return true
                }
            }
        }
    }

    if (processor.name == null && Namespace.Types in ns) {
        for ((traitPath, traitVisibility) in modData.unnamedTraitImports) {
            val trait = VisItem(traitPath, traitVisibility)
            val visibilityFilter = traitVisibility.createFilter(info)
            for (traitPsi in trait.toPsi(info, Namespace.Types)) {
                if (processor("_", traitPsi, visibilityFilter)) return true
            }
        }
    }

    if (ipm.withExternCrates && Namespace.Types in ns && scope is RsMod) {
        for ((name, externCrateDefMap) in defMap.externPrelude.entriesWithName(processor.name)) {
            val existingItemInScope = modData.visibleItems[name]
            if (existingItemInScope != null && existingItemInScope.types.any { !it.visibility.isInvisible }) continue

            val externCrateRoot = externCrateDefMap.root.toRsMod(info)
                // crate root can't multiresolve
                .singleOrNull()
                ?: continue
            processor(name, externCrateRoot) && return true
        }
    }

    return false
}

/** null return value means that new resolve can't be used. */
fun processMacros(
    scope: RsItemsOwner,
    processor: RsResolveProcessor,
    /**
     * `RsPath` in resolve, `PsiElement(identifier)` in completion by [RsMacroCompletionProvider].
     * Needed to filter textual scoped macros if path is unqualified.
     * null if path is qualified.
     */
    macroPath: PsiElement?,
    isAttrOrDerive: Boolean,
): Boolean? {
    val info = when (val info = getModInfo(scope)) {
        is CantUseNewResolve -> return null
        InfoNotFound -> return false
        is RsModInfo -> info
    }

    return info.modData.processMacros(macroPath, isAttrOrDerive, processor, info)
}

private fun ModData.processMacros(
    /** null if path is qualified */
    macroPath: PsiElement?,
    isAttrOrDerive: Boolean,
    processor: RsResolveProcessor,
    info: RsModInfo,
): Boolean {
    val isQualified = macroPath == null || macroPath is RsPath && macroPath.qualifier != null

    val stop = processScopedMacros(processor, info) { name ->
        val isLegacyMacroDeclaredInSameMod = !isQualified && legacyMacros[name].orEmpty().any {
            (!isAttrOrDerive || it is ProcMacroDefInfo) && it.path.parent == path
        }
        // Resolve order for unqualified macros:
        // - textual macros in same mod
        // - scoped macros (imported by `use`)
        // - textual macros
        !isLegacyMacroDeclaredInSameMod
    }
    if (stop) return true

    if (!isQualified) {
        check(macroPath != null)
        val macroIndex = info.getMacroIndex(macroPath, info.crate)
        for ((name, macroInfos) in legacyMacros.entriesWithName(processor.name)) {
            val macroInfo = if (!isAttrOrDerive) {
                filterMacrosByIndex(macroInfos, macroIndex)
            } else {
                macroInfos.lastOrNull { it is ProcMacroDefInfo }
            } ?: continue
            val visItem = VisItem(macroInfo.path, Visibility.Public)
            val macroContainingScope = visItem.containingMod.toScope(info).singleOrNull() ?: continue
            val macro = macroInfo.legacyMacroToPsi(macroContainingScope, info) ?: continue
            if (processor(name, macro)) return true
        }

        info.defMap.prelude?.let { prelude ->
            if (prelude.processScopedMacros(processor, info)) return true
        }
    }

    return false
}

private fun ModData.processScopedMacros(
    processor: RsResolveProcessor,
    info: RsModInfo,
    filter: (name: String) -> Boolean = { true },
): Boolean {
    for ((name, perNs) in visibleItems.entriesWithName(processor.name)) {
        for (visItem in perNs.macros) {
            if (!filter(name)) continue
            val macro = visItem.scopedMacroToPsi(info) ?: continue
            val visibilityFilter = visItem.visibility.createFilter(info)
            if (processor(name, macro, visibilityFilter)) return true
        }
    }
    return false
}

private fun filterMacrosByIndex(macroInfos: List<MacroDefInfo>, macroIndex: MacroIndex?): MacroDefInfo? =
    when {
        macroIndex != null -> macroInfos.getLastBefore(macroIndex)
        else -> macroInfos.last()  // this is kind of error, can choose anything here
    }

fun <T> RsPossibleMacroCall.resolveToMacroUsingNewResolveAndThen(
    withNewResolve: (RsNamedElement?) -> T,
    withoutNewResolve: () -> T
): T? =
    resolveToMacroAndThen(withoutNewResolve) { defInfo, info ->
        val visItem = VisItem(defInfo.path, Visibility.Public)
        val def = visItem.scopedMacroToPsi(info)
        withNewResolve(def)
    }

/**
 * Resolve without PSI is needed to prevent caching incomplete result in [expandedItemsCached].
 * Consider:
 * - Macro expansion task wants to expand some macro
 * - Firstly we resolve macro path
 * - It can trigger items resolve [processItemDeclarations2].
 *   E.g. if macro path is two segment - we need to resolve first segment as mod
 * - [processItemDeclarations2] uses [expandedItemsCached] which will try to expand all macros in scope,
 *   which results in recursion,
 *   which is prevented by returning null from macro expansion,
 *   therefore result of [expandedItemsCached] is incomplete (and cached)
 */
fun RsMacroCall.resolveToMacroWithoutPsi(): RsResult<RsMacroDataWithHash<*>, ResolveMacroWithoutPsiError> =
    resolveToMacroAndThen(
        withNewResolve = { def, _ -> RsMacroDataWithHash.fromDefInfo(def) },
        withoutNewResolve = {
            val psi = path.reference?.resolve() as? RsNamedElement
            psi?.let { RsMacroDataWithHash.fromPsi(it) }?.let { RsResult.Ok(it) }
        }
    ) ?: RsResult.Err(ResolveMacroWithoutPsiError.Unresolved)

/** See [resolveToMacroWithoutPsi] */
fun RsMacroCall.resolveToMacroAndGetContainingCrate(): Crate? =
    resolveToMacroAndThen(
        withNewResolve = { def, _ -> project.crateGraph.findCrateById(def.crate) },
        withoutNewResolve = { resolveToMacro()?.containingCrate }
    )

/** See [resolveToMacroWithoutPsi] */
fun RsMacroCall.resolveToMacroAndProcessLocalInnerMacros(
    processor: RsResolveProcessor,
    withoutNewResolve: () -> Boolean?
): Boolean? =
    resolveToMacroAndThen(withoutNewResolve) { def, info ->
        if (def !is DeclMacroDefInfo || !def.hasLocalInnerMacros) return@resolveToMacroAndThen null
        val project = info.project
        val defMap = project.defMapService.getOrUpdateIfNeeded(def.crate) ?: return@resolveToMacroAndThen null
        defMap.root.processMacros(
            macroPath = null,  // null because we resolve qualified macro path
            isAttrOrDerive = false,
            processor = processor,
            info = info,
        )
    }

/**
 * If new resolve can be used, computes result using [withNewResolve].
 * Otherwise fallbacks to [withoutNewResolve].
 */
private fun <T> RsPossibleMacroCall.resolveToMacroAndThen(
    withoutNewResolve: () -> T?,
    withNewResolve: (MacroDefInfo, RsModInfo) -> T?
): T? {
    val path = path ?: return null
    val info = when {
        !project.isNewResolveEnabled -> CantUseNewResolve("not enabled")
        isTopLevelExpansion || path.qualifier != null -> getModInfo(containingMod)
        else -> CantUseNewResolve("not top level")
    }
    return when (info) {
        is CantUseNewResolve -> withoutNewResolve()
        InfoNotFound -> null
        is RsModInfo -> {
            val def = when (val kind = kind) {
                is RsPossibleMacroCallKind.MacroCall -> kind.call.resolveToMacroDefInfo(info)
                is RsPossibleMacroCallKind.MetaItem -> kind.meta.resolveToProcMacroWithoutPsi()
            } ?: return null
            withNewResolve(def, info)
        }
    }
}

fun RsMetaItem.resolveToProcMacroWithoutPsi(checkIsMacroAttr: Boolean = true): ProcMacroDefInfo? {
    val owner = owner as? RsAttrProcMacroOwner ?: return null

    val info = when {
        !project.isNewResolveEnabled -> CantUseNewResolve("not enabled")
        RsProcMacroPsiUtil.canBeProcMacroCall(this) -> getModInfo(owner.containingMod)
        else -> CantUseNewResolve("not a proc macro")
    } as? RsModInfo ?: return null

    if (checkIsMacroAttr && !isMacroCall) return null

    if (owner.context?.existsAfterExpansion != true) return null

    val (_, defMap, modData) = info
    val macroPath = path?.pathSegmentsAdjustedForAttrMacro?.toTypedArray() ?: return null
    if (macroPath.size == 1) {
        val name = macroPath.single()
        modData.legacyMacros[name]
            ?.filterIsInstance<ProcMacroDefInfo>()
            ?.lastOrNull()
            ?.let { return it }
    }
    val perNs = defMap.resolvePathFp(
        modData,
        macroPath,
        ResolveMode.OTHER,
        withInvisibleItems = false  // because we expand only cfg-enabled macros
    )
    val defItem = perNs.resolvedDef.macros.singleOrNull() ?: return null
    return defMap.getMacroInfo(defItem) as? ProcMacroDefInfo
}

private fun RsMacroCall.resolveToMacroDefInfo(containingModInfo: RsModInfo): MacroDefInfo? {
    val (project, defMap, modData, crate) = containingModInfo
    return RsResolveCache.getInstance(project)
        .resolveWithCaching(this, RsMacroPathReferenceImpl.cacheDep) {
            val callPath = path.pathSegmentsAdjusted?.toTypedArray() ?: return@resolveWithCaching null
            val macroIndex = containingModInfo.getMacroIndex(this, crate) ?: return@resolveWithCaching null
            defMap.resolveMacroCallToMacroDefInfo(modData, callPath, macroIndex)
        }
}

@VisibleForTesting
fun RsModInfo.getMacroIndex(element: PsiElement, elementCrate: Crate): MacroIndex? {
    if (element is RsMetaItem) {
        val owner = element.owner as? RsAttrProcMacroOwner
        if (owner != null && RsProcMacroPsiUtil.canBeCustomDerive(element)) {
            val ownerIndex = getMacroIndex(owner, elementCrate) ?: return null
            val attr = ProcMacroAttribute.getProcMacroAttributeWithoutResolve(
                owner,
                explicitCrate = crate,
                withDerives = true
            )
            val deriveIndex = when (attr) {
                is ProcMacroAttribute.Derive -> attr.derives.indexOf(element)
                else -> 0
            }
            return ownerIndex.append(deriveIndex)
        }
    }
    val itemAndCallExpandedFrom = element.stubAncestors
        .filterIsInstance<RsExpandedElement>()
        .mapNotNull { it to (it.expandedOrIncludedFrom ?: return@mapNotNull null) }
        .firstOrNull()
    if (itemAndCallExpandedFrom == null) {
        if (element.containingFile is RsCodeFragment) return MacroIndex(intArrayOf(Int.MAX_VALUE))
        val (item, parent) = element.ancestorPairs.first { (_, parent) -> parent is RsMod }
        val modData = getModData(parent as RsMod, elementCrate.id ?: return null) ?: return null
        val indexInParent = getMacroIndexInParent(item, parent, elementCrate)
        return modData.macroIndex.append(indexInParent)
    } else {
        val (item, callExpandedFrom) = itemAndCallExpandedFrom
        val parent = item.parent
        // TODO: Possible optimization - store macro index in [resolveToMacroDefInfo] cache
        val parentIndex = getMacroIndex(callExpandedFrom, elementCrate) ?: return null
        if (parent !is RsMod) return parentIndex  // not top level expansion
        val indexInParent = getMacroIndexInParent(item, parent, elementCrate)
        return parentIndex.append(indexInParent)
    }
}

private fun getMacroIndexInParent(item: PsiElement, parent: PsiElement, crate: Crate): Int {
    val itemStub = (item as? StubBasedPsiElement<*>)?.greenStub
    val parentStub = if (parent is PsiFileBase) parent.greenStub else (parent as? StubBasedPsiElement<*>)?.greenStub
    return if (itemStub != null && parentStub != null) {
        parentStub.childrenStubs.asSequence()
            .takeWhile { it !== itemStub }
            .count { it.hasMacroIndex(crate) }
    } else {
        parent.children.asSequence()
            .takeWhile { it !== item }
            .count { it.hasMacroIndex(crate) }
    }
}

private val RsPath.pathSegments: List<String>?
    get() {
        val segments = mutableListOf<String>()
        var path: RsPath? = this
        while (path != null) {
            segments += path.referenceName ?: return null
            val qualifier = path.path
            if (qualifier == null && path.hasColonColon) {
                // ::crate_name::macro!()
                segments += ""
            }
            path = qualifier
        }
        segments.reverse()
        return segments
    }

/**
 * Adjustment is performed according to [getPathKind]:
 * - If macro call is expanded from macro def with `#[macro_export(local_inner_macros)]` attribute:
 *   before: `foo!();`
 *   after:  `IntellijRustDollarCrate::12345::foo!();`
 *                                     ~~~~~ crateId
 * - If macro call starts with [MACRO_DOLLAR_CRATE_IDENTIFIER]:
 *   before: `IntellijRustDollarCrate::foo!();`
 *   after:  `IntellijRustDollarCrate::12345::foo!();`
 *                                     ~~~~~ crateId
 *
 * See also [processMacroCallPathResolveVariants] and [findDependencyCrateByNamePath]
 */
private val RsPath.pathSegmentsAdjusted: List<String>?
    get() {
        val segments = pathSegments ?: return null

        val callExpandedFrom = findMacroCallExpandedFromNonRecursive() as? RsMacroCall ?: return segments
        val (defExpandedFromHasLocalInnerMacros, defExpandedFromCrateId) =
            when (val info = getModInfo(callExpandedFrom.containingMod)) {
                is CantUseNewResolve -> {
                    val expandedFrom = callExpandedFrom.resolveToMacro() ?: return segments
                    val crateId = expandedFrom.containingCrate?.id ?: return segments
                    val hasMacroExportLocalInnerMacros = expandedFrom is RsMacro && expandedFrom.hasMacroExportLocalInnerMacros
                    hasMacroExportLocalInnerMacros to crateId
                }
                InfoNotFound -> return segments
                is RsModInfo -> {
                    val def = callExpandedFrom.resolveToMacroDefInfo(info) ?: return segments
                    (def is DeclMacroDefInfo && def.hasLocalInnerMacros) to def.crate
                }
            }
        return when {
            segments.size == 1 && !cameFromMacroCall() && defExpandedFromHasLocalInnerMacros -> {
                listOf(MACRO_DOLLAR_CRATE_IDENTIFIER, defExpandedFromCrateId.toString()) + segments
            }
            segments.first() == MACRO_DOLLAR_CRATE_IDENTIFIER -> {
                val crate = resolveDollarCrateIdentifier()?.id ?: return segments
                listOf(MACRO_DOLLAR_CRATE_IDENTIFIER, crate.toString()) + segments.subList(1, segments.size)
            }
            else -> segments
        }
    }

/**
 * Similar to [pathSegmentsAdjusted], but doesn't take into account `#[macro_export(local_inner_macros)]`
 */
private val RsPath.pathSegmentsAdjustedForAttrMacro: List<String>?
    get() {
        val segments = pathSegments ?: return null
        return when {
            segments.first() == MACRO_DOLLAR_CRATE_IDENTIFIER -> {
                val crate = this.resolveDollarCrateIdentifier()?.id ?: return segments
                listOf(MACRO_DOLLAR_CRATE_IDENTIFIER, crate.toString()) + segments.subList(1, segments.size)
            }
            else -> segments
        }
    }

sealed class RsModInfoBase {
    /** [reason] is only for debug */
    class CantUseNewResolve(val reason: String) : RsModInfoBase()
    object InfoNotFound : RsModInfoBase()
    data class RsModInfo(
        val project: Project,
        val defMap: CrateDefMap,
        val modData: ModData,
        val crate: Crate,
        val dataPsiHelper: DataPsiHelper?,
    ) : RsModInfoBase()
}

interface DataPsiHelper {
    fun psiToData(scope: RsItemsOwner): ModData?
    fun dataToPsi(data: ModData): RsItemsOwner?
    fun findModData(path: ModPath): ModData? = null
}

fun getModInfo(scope0: RsItemsOwner): RsModInfoBase {
    val scope = scope0.originalElement as? RsItemsOwner ?: scope0
    if (scope !is RsMod) return getHangingModInfo(scope)
    val project = scope.project
    if (!project.isNewResolveEnabled) return CantUseNewResolve("not enabled")
    if (scope is RsModItem && scope.modName == TMP_MOD_NAME) return CantUseNewResolve("__tmp__ mod")
    if (scope.isLocal) return CantUseNewResolve("local mod")
    val crate = when (val crate = scope.containingCrate) {
        is CargoBasedCrate -> crate
        is DoctestCrate -> return CantUseNewResolve("Doctest crate")
        null -> return project.getDetachedModInfo(scope)
        else -> error("unreachable")
    }
    if (crate.rootModFile != null && !shouldIndexFile(project, crate.rootModFile)) {
        return CantUseNewResolve("crate root isn't indexed")
    }

    val defMap = project.getDefMap(crate) ?: return InfoNotFound
    val modData = defMap.getModData(scope) ?: return InfoNotFound

    if (isModShadowedByOtherMod(scope, modData, crate)) return CantUseNewResolve("mod shadowed by other mod")

    return RsModInfo(project, defMap, modData, crate, null)
}

private fun Project.getDefMap(crate: Crate): CrateDefMap? {
    check(crate !is DoctestCrate) { "doc test crates are not supported by CrateDefMap" }
    val crateId = crate.id ?: return null
    val defMap = defMapService.getOrUpdateIfNeeded(crateId)
    if (defMap == null) {
        RESOLVE_LOG.warn("DefMap is null for $crate during resolve")
    }
    return defMap
}

private fun RsModInfo.getModData(mod: RsMod, modCrate: CratePersistentId): ModData? {
    dataPsiHelper?.psiToData(mod)?.let { return it }
    return defMap.getDefMap(modCrate)?.getModData(mod)
}

/** E.g. `fn func() { mod foo { ... } }` */
private val RsMod.isLocal: Boolean
    get() = ancestorStrict<RsBlock>() != null

/** "shadowed by other mod" means that [ModData] is not accessible from [CrateDefMap.root] through [ModData.childModules] */
private fun isModShadowedByOtherMod(mod: RsMod, modData: ModData, crate: Crate): Boolean {
    val isDeeplyEnabledByCfg = (mod.containingFile as RsFile).isDeeplyEnabledByCfg && mod.isEnabledByCfg(crate)
    val isShadowedByOtherInlineMod = isDeeplyEnabledByCfg != modData.isDeeplyEnabledByCfg

    return modData.isShadowedByOtherFile || isShadowedByOtherInlineMod
}

private fun <T> Map<String, T>.entriesWithName(name: String?): Map<String, T> {
    if (name == null) {
        return this
    } else {
        val value = this[name] ?: return emptyMap()
        return mapOf(name to value)
    }
}

/** Creates filter which determines whether item with [this] visibility is visible from specific [RsMod] */
private fun Visibility.createFilter(info: RsModInfo): (RsElement) -> VisibilityStatus {
    if (this is Visibility.Restricted) {
        val inMod = inMod.toRsMod(info)
        if (inMod.isNotEmpty()) {
            return { context ->
                val modOpenedInEditor = context.containingModOrSelf
                val visible = inMod.any(modOpenedInEditor.superMods::contains)
                if (visible) VisibilityStatus.Visible else VisibilityStatus.Invisible
            }
        }
    }
    // cfg-disabled items should resolve only from cfg-disabled modules
    if (this === Visibility.CfgDisabled) {
        return filter@{ context ->
            val file = context.containingFile as? RsFile ?: return@filter VisibilityStatus.Visible
            // ignore doc test crates
            val crate = file.containingCrate as? CargoBasedCrate ?: return@filter VisibilityStatus.Visible
            val isDeeplyEnabledByCfg = context.isEnabledByCfg(crate) && file.isDeeplyEnabledByCfg
            if (isDeeplyEnabledByCfg) VisibilityStatus.CfgDisabled else VisibilityStatus.Visible
        }
    }
    return { VisibilityStatus.Visible }
}

private fun VisItem.scopedMacroToPsi(info: RsModInfo): RsNamedElement? {
    val containingScope = containingMod.toScope(info).singleOrNull() ?: return null
    return scopedMacroToPsi(containingScope)
}

private fun VisItem.scopedMacroToPsi(containingScope: RsItemsOwner): RsNamedElement? {
    val items = containingScope.expandedItemsCached
    val legacyMacros = items.legacyMacros
        .filter { it.name == name && matchesIsEnabledByCfg(it, this) }
    if (legacyMacros.isNotEmpty()) return legacyMacros.singlePublicOrFirst()

    if (name !in KNOWN_DERIVABLE_TRAITS || containingScope.containingCrate?.origin != PackageOrigin.STDLIB) {
        items.named[name]
            ?.singleOrNull { it is RsMacro2 && matchesIsEnabledByCfg(it, this) }
            ?.let { return it as RsMacro2 }
    }

    return items.named.values
        .flatten()
        .filterIsInstance<RsFunction>()
        .singleOrNull {
            it.isProcMacroDef && it.procMacroName == name && matchesIsEnabledByCfg(it, this)
        }
}

private fun MacroDefInfo.legacyMacroToPsi(containingScope: RsItemsOwner, info: RsModInfo): RsElement? {
    val items = containingScope.expandedItemsCached
    return when (this) {
        is DeclMacroDefInfo -> items.legacyMacros.singleOrNull {
            val crate = info.project.crateGraph.findCrateById(crate) ?: return@singleOrNull false
            val defIndex = info.getMacroIndex(it, crate) ?: return@singleOrNull false
            MacroIndex.equals(defIndex, macroIndex)
        }
        is ProcMacroDefInfo, is DeclMacro2DefInfo -> items.named[path.name]?.firstOrNull()
    }
    // Note that we can return null, e.g. if old macro engine is enabled and macro definition is itself expanded
}

fun VisItem.toPsi(info: RsModInfo, ns: Namespace): List<RsNamedElement> {
    if (isModOrEnum) return path.toRsModOrEnum(info)

    val containingModData = info.findModData(containingMod) ?: return emptyList()
    return if (containingModData.isEnum) {
        val containingEnums = containingModData.toRsEnum(info)
        containingEnums.flatMap { containingEnum ->
            containingEnum.variants
                .filter { it.name == name && ns in it.namespaces && matchesIsEnabledByCfg(it, this) }
        }
    } else {
        val containingMods = containingModData.toScope(info)
        containingMods.flatMap { containingMod ->
            if (ns == Namespace.Macros) {
                val macro = scopedMacroToPsi(containingMod)
                listOfNotNull(macro)
            } else {
                containingMod
                    .getExpandedItemsWithName<RsNamedElement>(name)
                    .filter { ns in it.namespaces && matchesIsEnabledByCfg(it, this) }
            }
        }
    }
}

private fun matchesIsEnabledByCfg(itemPsi: RsNamedElement, item: VisItem): Boolean =
    if (item.isEnabledByCfg) {
        itemPsi.isEnabledByCfg
    } else {
        // If inside cfg-disabled module we have import to cfg-enabled item,
        // then this item will be imported with CfgDisabled visibility
        true
    }

private val VisItem.isEnabledByCfg: Boolean get() = visibility != Visibility.CfgDisabled

private fun RsModInfo.findModData(path: ModPath): ModData? =
    dataPsiHelper?.findModData(path) ?: defMap.getModData(path)

private fun ModPath.toScope(info: RsModInfo): List<RsItemsOwner> {
    val modData = info.findModData(this)
    if (modData == null || modData.isEnum) return emptyList()
    return modData.toScope(info)
}

private fun ModPath.toRsModOrEnum(info: RsModInfo): List<RsNamedElement /* RsMod or RsEnumItem */> {
    val modData = info.findModData(this) ?: return emptyList()
    return if (modData.isEnum) {
        modData.toRsEnum(info)
    } else {
        modData.toRsMod(info)
    }
}

private fun ModData.toRsEnum(info: RsModInfo): List<RsEnumItem> {
    if (!isEnum || parent == null) return emptyList()
    val containingScopes = parent.toScope(info)
    val visItem = asVisItem()
    return containingScopes.flatMap { containingScope ->
        containingScope
            .getExpandedItemsWithName<RsEnumItem>(name)
            .filter { matchesIsEnabledByCfg(it, visItem) }
    }
}

fun ModData.toRsMod(info: RsModInfo): List<RsMod> = toScope(info).filterIsInstance<RsMod>()

fun ModData.toScope(info: RsModInfo): List<RsItemsOwner> {
    info.dataPsiHelper?.dataToPsi(this)?.let { return listOf(it) }
    return toRsMod(info.project)
}

fun ModData.toRsMod(project: Project): List<RsMod> = toRsModNullable(project)
    .also {
        if (it.isEmpty()) {
            RESOLVE_LOG.warn("Can't find RsMod for $this")
        }
    }

private fun ModData.toRsModNullable(project: Project): List<RsMod> {
    if (isEnum || fileId == null) return emptyList()
    val file = PersistentFS.getInstance().findFileById(fileId)
        ?.toPsiFile(project) as? RsFile
        ?: return emptyList()
    if (isRsFile) return listOf(file)

    val visItem = asVisItem()
    val fileRelativeSegments = fileRelativePath.split("::")
    return fileRelativeSegments
        .subList(1, fileRelativeSegments.size)
        .fold(listOf<RsMod>(file)) { mods, segment ->
            mods.flatMap { mod ->
                mod
                    .getExpandedItemsWithName<RsModItem>(segment)
                    .filter { matchesIsEnabledByCfg(it, visItem) }
            }
        }
}

private inline fun <reified T : RsNamedElement> RsItemsOwner.getExpandedItemsWithName(name: String): List<T> {
    val itemsCfgEnabled = expandedItemsCached.named[name]?.filterIsInstance<T>()
    if (itemsCfgEnabled != null && itemsCfgEnabled.isNotEmpty()) return itemsCfgEnabled
    return expandedItemsCached.namedCfgDisabled[name]?.filterIsInstance<T>() ?: emptyList()
}

/**
 * If all def maps are up-to-date, then returns correct result
 * Otherwise may return null even if [file] has correct [ModData]
 */
fun findModDataFor(file: RsFile): ModData? {
    val project = file.project
    check(project.isNewResolveEnabled)
    val defMapService = project.defMapService
    val virtualFile = file.virtualFile as? VirtualFileWithId ?: return null
    // TODO Ensure def maps are up-to-date (`findCrates` may return old crate of def maps haven't updated).
    return defMapService
        .findCrates(file)
        .mapNotNull { crateId ->
            val defMap = defMapService.getOrUpdateIfNeeded(crateId) ?: return@mapNotNull null
            val fileInfo = defMap.fileInfos[virtualFile.id] ?: return@mapNotNull null
            fileInfo.modData
        }
        .singleOrFirstCfgEnabled()
}

private fun List<ModData>.singleOrFirstCfgEnabled(): ModData? =
    singleOrNull() ?: firstOrNull { it.isDeeplyEnabledByCfg } ?: firstOrNull()
