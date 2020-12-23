/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import org.rust.cargo.project.settings.rustSettings
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.core.completion.RsMacroCompletionProvider
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.crate.impl.CargoBasedCrate
import org.rust.lang.core.crate.impl.DoctestCrate
import org.rust.lang.core.macros.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS
import org.rust.lang.core.resolve.ref.RsMacroPathReferenceImpl
import org.rust.lang.core.resolve.ref.RsResolveCache
import org.rust.lang.core.resolve2.RsModInfoBase.*
import org.rust.openapiext.testAssert
import org.rust.openapiext.toPsiFile

val Project.isNewResolveEnabled: Boolean
    get() = rustSettings.newResolveEnabled

/** null return value means that new resolve can't be used */
fun processItemDeclarations2(
    scope: RsMod,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    ipm: ItemProcessingMode
): Boolean? {
    val (project, defMap, modData) = when (val info = getModInfo(scope)) {
        is CantUseNewResolve -> return null
        InfoNotFound -> return false
        is RsModInfo -> info
    }

    val isCompletion = processor.name == null
    for ((name, perNs) in modData.visibleItems.entriesWithName(processor.name)) {
        val visItems = arrayOf(
            perNs.types to Namespace.Types,
            perNs.values to Namespace.Values,
            perNs.macros to Namespace.Macros,
        )
        // We need a `Set` here because item could belong to multiple namespaces (e.g. unit struct)
        // Also we need to distinguish unit struct and e.g. mod and function with same name in one module
        val elements = hashSetOf<RsNamedElement>()
        for ((visItem, namespace) in visItems) {
            if (visItem == null || namespace !in ns) continue
            if (ipm == WITHOUT_PRIVATE_IMPORTS && visItem.visibility == Visibility.Invisible) continue
            val visibilityFilter = visItem.visibility.createFilter(project, isCompletion)
            for (element in visItem.toPsi(defMap, project, namespace)) {
                if (!elements.add(element)) continue
                if (processor(name, element, visibilityFilter)) return true
            }
        }
    }

    if (processor.name == null && Namespace.Types in ns) {
        for ((traitPath, traitVisibility) in modData.unnamedTraitImports) {
            val trait = VisItem(traitPath, traitVisibility)
            val visibilityFilter = traitVisibility.createFilter(project, isCompletion)
            for (traitPsi in trait.toPsi(defMap, project, Namespace.Types)) {
                if (processor("_", traitPsi, visibilityFilter)) return true
            }
        }
    }

    if (ipm.withExternCrates && Namespace.Types in ns) {
        for ((name, externCrateDefMap) in defMap.externPrelude.entriesWithName(processor.name)) {
            if (modData.visibleItems[name]?.types != null) continue
            val externCrateRoot = externCrateDefMap.root.toRsMod(project) ?: continue
            processor(name, externCrateRoot) && return true
        }
    }

    return false
}

/**
 * null return value means that new resolve can't be used.
 * [runBeforeResolve] is passed to conform with [MacroResolver],
 * and it should be called only if we are going to use new resolve.
 * We need to get [ModData] to check if we can use new resolve, which is not fast,
 * so we unite check and actual resolve as an optimization.
 */
fun processMacros(
    scope: RsMod,
    processor: RsResolveProcessor,
    /**
     * `RsPath` in resolve, `PsiElement(identifier)` in completion by [RsMacroCompletionProvider].
     * Needed to filter textual scoped macros if path is unqualified.
     * null if path is qualified.
     */
    macroPath: PsiElement?,
    runBeforeResolve: (() -> Boolean)? = null
): Boolean? {
    val (project, defMap, modData) = when (val info = getModInfo(scope)) {
        is CantUseNewResolve -> return null
        InfoNotFound -> return false
        is RsModInfo -> info
    }
    if (runBeforeResolve != null && runBeforeResolve()) return true

    return modData.processMacros(scope, macroPath, processor, defMap, project)
}

private fun ModData.processMacros(
    /** Must be not null during completion */
    scope: RsMod?,
    /** null if path is qualified */
    macroPath: PsiElement?,
    processor: RsResolveProcessor,
    defMap: CrateDefMap,
    project: Project,
): Boolean {
    val isCompletion = processor.name == null

    val isQualified = macroPath == null || macroPath is RsPath && macroPath.qualifier != null
    if (!isQualified) {
        check(macroPath != null)
        val exactScopeVisibilityFilter = if (isCompletion) { mod: RsMod -> mod == scope } else { _ -> true }
        val macroIndex = getMacroIndex(macroPath, defMap)
        for ((name, macroInfos) in legacyMacros.entriesWithName(processor.name)) {
            val macroInfo = filterMacrosByIndex(macroInfos, macroIndex) ?: continue
            val visItem = VisItem(macroInfo.path, Visibility.Public)
            if (visItem.path == visibleItems[name]?.macros?.path) continue  // macros will be handled in [visibleItems] loop
            val macroContainingMod = visItem.containingMod.toRsMod(defMap, project) ?: continue
            val macroDefMap = defMap.getDefMap(macroInfo.crate) ?: continue
            val macro = macroInfo.legacyMacroToPsi(macroContainingMod, macroDefMap) ?: continue
            if (processor(name, macro, exactScopeVisibilityFilter)) return true
        }
    }

    for ((name, perNs) in visibleItems.entriesWithName(processor.name)) {
        val visItem = perNs.macros ?: continue
        val macro = visItem.scopedMacroToPsi(defMap, project) ?: continue
        val visibilityFilter = visItem.visibility.createFilter(project, isCompletion)
        if (processor(name, macro, visibilityFilter)) return true
    }

    return false
}

private fun filterMacrosByIndex(macroInfos: List<MacroDefInfo>, macroIndex: MacroIndex?): MacroDefInfo? =
    when {
        macroIndex != null -> macroInfos.getLastBefore(macroIndex)
        else -> macroInfos.last()  // this is kind of error, can choose anything here
    }

fun RsMacroCall.resolveToMacroUsingNewResolve(): RsNamedElement? =
    resolveToMacroAndThen(
        withNewResolve = { defInfo, info ->
            val visItem = VisItem(defInfo.path, Visibility.Public)
            visItem.scopedMacroToPsi(info.defMap, info.project)
        },
        withoutNewResolve = { it }
    )

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
fun RsMacroCall.resolveToMacroWithoutPsi(): RsMacroDataWithHash? =
    resolveToMacroAndThen(
        withNewResolve = { def, _ -> RsMacroDataWithHash(RsMacroData(def.body), def.bodyHash) },
        withoutNewResolve = { def -> RsMacroDataWithHash(def) }
    )

/** See [resolveToMacroWithoutPsi] */
fun RsMacroCall.resolveToMacroAndGetContainingCrate(): Crate? =
    resolveToMacroAndThen(
        withNewResolve = { def, _ -> project.crateGraph.findCrateById(def.crate) },
        withoutNewResolve = { def -> def.containingCrate }
    )

/** See [resolveToMacroWithoutPsi] */
fun RsMacroCall.resolveToMacroAndProcessLocalInnerMacros(
    processor: RsResolveProcessor,
    withoutNewResolve: (RsMacro) -> Boolean?
): Boolean? =
    resolveToMacroAndThen(withoutNewResolve) { def, info ->
        if (!def.hasLocalInnerMacros) return@resolveToMacroAndThen null
        val project = info.project
        val defMap = project.defMapService.getOrUpdateIfNeeded(def.crate) ?: return@resolveToMacroAndThen null
        defMap.root.processMacros(
            scope = null,  // null because it is not completion
            macroPath = null,  // null because we resolve qualified macro path
            processor = processor,
            defMap = defMap,
            project = project
        )
    }

/**
 * If new resolve can be used, computes result using [withNewResolve].
 * Otherwise fallbacks to [withoutNewResolve].
 */
private fun <T> RsMacroCall.resolveToMacroAndThen(
    withoutNewResolve: (RsMacro) -> T?,
    withNewResolve: (MacroDefInfo, RsModInfo) -> T?
): T? {
    if (!project.isNewResolveEnabled) {
        val def = resolveToMacro() ?: return null
        return withoutNewResolve(def)
    }
    @Suppress("MoveVariableDeclarationIntoWhen")
    val info = if (isTopLevelExpansion) getModInfo(containingMod) else CantUseNewResolve("not top level")
    return when (info) {
        is CantUseNewResolve -> {
            val def = resolveToMacro() ?: return null
            withoutNewResolve(def)
        }
        InfoNotFound -> null
        is RsModInfo -> {
            val def = resolveToMacroDefInfo(info) ?: return null
            withNewResolve(def, info)
        }
    }
}

private fun RsMacroCall.resolveToMacroDefInfo(containingModInfo: RsModInfo): MacroDefInfo? {
    val (project, defMap, modData) = containingModInfo
    return RsResolveCache.getInstance(project)
        .resolveWithCaching(this, RsMacroPathReferenceImpl.cacheDep) {
            val callPath = pathSegmentsAdjusted?.toTypedArray() ?: return@resolveWithCaching null
            val macroIndex = getMacroIndex(this, defMap) ?: return@resolveWithCaching null
            defMap.resolveMacroCallToMacroDefInfo(modData, callPath, macroIndex)
        }
}

private fun getMacroIndex(element: PsiElement, defMap: CrateDefMap): MacroIndex? {
    val itemAndCallExpandedFrom = element.stubAncestors
        .filterIsInstance<RsExpandedElement>()
        .mapNotNull { it to (it.expandedFrom ?: return@mapNotNull null) }
        .firstOrNull()
    if (itemAndCallExpandedFrom == null) {
        if (element.containingFile is RsCodeFragment) return MacroIndex(intArrayOf(Int.MAX_VALUE))
        val (item, parent) = element.ancestorPairs.first { (_, parent) -> parent is RsMod }
        val modData = defMap.getModData(parent as RsMod) ?: return null
        val indexInParent = getMacroIndexInParent(item, parent)
        return modData.macroIndex.append(indexInParent)
    } else {
        val (item, callExpandedFrom) = itemAndCallExpandedFrom
        val parent = item.parent
        // TODO: Possible optimization - store macro index in [resolveToMacroDefInfo] cache
        val parentIndex = getMacroIndex(callExpandedFrom, defMap) ?: return null
        if (parent !is RsMod) return parentIndex  // not top level expansion
        val indexInParent = getMacroIndexInParent(item, parent)
        return parentIndex.append(indexInParent)
    }
}

private fun getMacroIndexInParent(item: PsiElement, parent: PsiElement): Int {
    val itemStub = (item as? StubBasedPsiElement<*>)?.greenStub
    val parentStub = if (parent is PsiFileBase) parent.greenStub else (parent as? StubBasedPsiElement<*>)?.greenStub
    return if (itemStub != null && parentStub != null) {
        parentStub.childrenStubs.asSequence()
            .takeWhile { it !== itemStub }
            .count { it.hasMacroIndex() }
    } else {
        parent.children.asSequence()
            .takeWhile { it !== item }
            .count { it.hasMacroIndex() }
    }
}

private val RsMacroCall.pathSegments: List<String>?
    get() {
        val segments = generateSequence(path) { it.path }
            .map { it.referenceName }
            .toMutableList()
        if (segments.any { it == null }) return null
        segments.reverse()
        return segments.requireNoNulls()
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
private val RsMacroCall.pathSegmentsAdjusted: List<String>?
    get() {
        val segments = pathSegments ?: return null

        val callExpandedFrom = findMacroCallExpandedFromNonRecursive() ?: return segments
        val (defExpandedFromHasLocalInnerMacros, defExpandedFromCrateId) =
            when (val info = getModInfo(callExpandedFrom.containingMod)) {
                is CantUseNewResolve -> {
                    val expandedFrom = callExpandedFrom.resolveToMacro() ?: return segments
                    val crateId = expandedFrom.containingCrate?.id ?: return segments
                    expandedFrom.hasMacroExportLocalInnerMacros to crateId
                }
                InfoNotFound -> return segments
                is RsModInfo -> {
                    val def = callExpandedFrom.resolveToMacroDefInfo(info) ?: return segments
                    def.hasLocalInnerMacros to def.crate
                }
            }
        return when {
            segments.size == 1 && !path.cameFromMacroCall() && defExpandedFromHasLocalInnerMacros -> {
                listOf(MACRO_DOLLAR_CRATE_IDENTIFIER, defExpandedFromCrateId.toString()) + segments
            }
            segments.first() == MACRO_DOLLAR_CRATE_IDENTIFIER -> {
                val crate = path.resolveDollarCrateIdentifier()?.id ?: return segments
                listOf(MACRO_DOLLAR_CRATE_IDENTIFIER, crate.toString()) + segments.subList(1, segments.size)
            }
            else -> segments
        }
    }

private sealed class RsModInfoBase {
    /** [reason] is only for debug */
    class CantUseNewResolve(val reason: String) : RsModInfoBase()
    object InfoNotFound : RsModInfoBase()
    data class RsModInfo(val project: Project, val defMap: CrateDefMap, val modData: ModData) : RsModInfoBase()
}

private fun getModInfo(scope: RsMod): RsModInfoBase {
    val project = scope.project
    if (!project.isNewResolveEnabled) return CantUseNewResolve("not enabled")
    if (scope is RsModItem && scope.modName == TMP_MOD_NAME) return CantUseNewResolve("__tmp__ mod")
    if (scope.isLocal) return CantUseNewResolve("local mod")
    val crate = scope.containingCrate as? CargoBasedCrate ?: return CantUseNewResolve("not CargoBasedCrate")

    val defMap = project.getDefMap(crate) ?: return InfoNotFound
    val modData = defMap.getModData(scope) ?: return InfoNotFound

    if (isModShadowedByOtherMod(scope, modData)) return CantUseNewResolve("mod shadowed by other mod")

    return RsModInfo(project, defMap, modData)
}

private fun Project.getDefMap(crate: Crate): CrateDefMap? {
    check(crate !is DoctestCrate) { "doc test crates are not supported by CrateDefMap" }
    val crateId = crate.id ?: return null
    val defMap = defMapService.getOrUpdateIfNeeded(crateId)
    if (defMap == null) RESOLVE_LOG.error("DefMap is null for $crate during resolve")
    return defMap
}

/** E.g. `fn func() { mod foo { ... } }` */
private val RsMod.isLocal: Boolean
    get() = ancestorStrict<RsBlock>() != null

/** "shadowed by other mod" means that [ModData] is not accessible from [CrateDefMap.root] through [ModData.childModules] */
private fun isModShadowedByOtherMod(mod: RsMod, modData: ModData): Boolean {
    val isDeeplyEnabledByCfg = (mod.containingFile as RsFile).isDeeplyEnabledByCfg && mod.isEnabledByCfg
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
private fun Visibility.createFilter(project: Project, isCompletion: Boolean): (RsMod) -> Boolean {
    if (isCompletion && this is Visibility.Restricted) {
        val inMod = inMod.toRsMod(project)
        if (inMod != null) return { it.superMods.contains(inMod) }
    }
    /**
     * Note: [Visibility.Invisible] and [Visibility.CfgDisabled] are considered as [Visibility.Public],
     * because they are handled in other places, e.g. [matchesIsEnabledByCfg]
     */
    return { true }
}

private fun VisItem.scopedMacroToPsi(defMap: CrateDefMap, project: Project): RsNamedElement? {
    val containingMod = containingMod.toRsMod(defMap, project) ?: return null
    return scopedMacroToPsi(containingMod)
}

private fun VisItem.scopedMacroToPsi(containingMod: RsMod): RsNamedElement? {
    val items = containingMod.expandedItemsCached
    val macros = items.macros
        .filter { it.name == name && matchesIsEnabledByCfg(it, this) }
    if (macros.isNotEmpty()) return macros.singlePublicOrFirst()

    return items.named.values
        .flatten()
        .filterIsInstance<RsFunction>()
        .singleOrNull {
            it.isProcMacroDef && it.procMacroName == name && matchesIsEnabledByCfg(it, this)
        }
}

private fun MacroDefInfo.legacyMacroToPsi(containingMod: RsMod, defMap: CrateDefMap): RsMacro? {
    val items = containingMod.expandedItemsCached
    val macro = items.macros.singleOrNull {
        val defIndex = getMacroIndex(it, defMap) ?: return@singleOrNull false
        MacroIndex.equals(defIndex, macroIndex)
    }
    testAssert({ macro != null }, { "Can't convert MacroDefInfo to RsMacro using global MacroIndex" })
    return macro
}

private fun VisItem.toPsi(defMap: CrateDefMap, project: Project, ns: Namespace): List<RsNamedElement> {
    if (isModOrEnum) return path.toRsModOrEnum(defMap, project)
    val containingModOrEnum = containingMod.toRsModOrEnum(defMap, project).singleOrNull() ?: return emptyList()
    return when (containingModOrEnum) {
        is RsMod -> {
            if (ns == Namespace.Macros) {
                val macro = scopedMacroToPsi(containingModOrEnum)
                listOfNotNull(macro)
            } else {
                containingModOrEnum
                    .getExpandedItemsWithName<RsNamedElement>(name)
                    .filter { ns in it.namespaces && matchesIsEnabledByCfg(it, this) }
            }
        }
        is RsEnumItem -> {
            containingModOrEnum.variants
                .filter { it.name == name && ns in it.namespaces && matchesIsEnabledByCfg(it, this) }
        }
        else -> error("Expected mod or enum, got: $containingModOrEnum")
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

private fun ModPath.toRsMod(defMap: CrateDefMap, project: Project): RsMod? {
    val modData = defMap.getModData(this) ?: return null
    if (modData.isEnum) return null
    return modData.toRsMod(project)
}

private fun ModPath.toRsModOrEnum(defMap: CrateDefMap, project: Project): List<RsNamedElement /* RsMod or RsEnumItem */> {
    val modData = defMap.getModData(this) ?: return emptyList()
    return if (modData.isEnum) {
        modData.toRsEnum(project)
    } else {
        val mod = modData.toRsMod(project)
        listOfNotNull(mod)
    }
}

private fun ModData.toRsEnum(project: Project): List<RsEnumItem> {
    if (!isEnum) return emptyList()
    val containingMod = parent?.toRsMod(project) ?: return emptyList()
    val visItem = asVisItem()
    return containingMod
        .getExpandedItemsWithName<RsEnumItem>(name)
        .filter { matchesIsEnabledByCfg(it, visItem) }
}

private fun ModData.toRsMod(project: Project): RsMod? = toRsModNullable(project)
    ?: run {
        RESOLVE_LOG.warn("Can't find RsMod for $this")
        null
    }

private fun ModData.toRsModNullable(project: Project): RsMod? {
    if (isEnum) return null
    val file = PersistentFS.getInstance().findFileById(fileId)
        ?.toPsiFile(project) as? RsFile
        ?: return null
    val fileRelativeSegments = fileRelativePath.split("::")
    return fileRelativeSegments
        .subList(1, fileRelativeSegments.size)
        .fold(file as RsMod) { mod, segment ->
            mod
                .getExpandedItemsWithName<RsModItem>(segment)
                .singleOrCfgEnabled()
                ?: return null
        }
}

// TODO: Multiresolve
private inline fun <reified T : RsElement> List<T>.singleOrCfgEnabled(): T? =
    singleOrNull() ?: singleOrNull { it.isEnabledByCfg }

private inline fun <reified T : RsNamedElement> RsItemsOwner.getExpandedItemsWithName(name: String): List<T> =
    expandedItemsCached.named[name]?.filterIsInstance<T>() ?: emptyList()

fun findModDataFor(file: RsFile): ModData? {
    val project = file.project
    check(project.isNewResolveEnabled)
    val defMapService = project.defMapService
    val virtualFile = file.virtualFile as? VirtualFileWithId ?: return null
    // TODO Ensure def maps are up-to-date (`findCrate` may return old crate of def maps haven't updated).
    //   Now this is used only in the macro expansion engine where all def map are always up-to-date
    val crateId = defMapService.findCrate(file) ?: return null
    val defMap = defMapService.getOrUpdateIfNeeded(crateId) ?: return null
    val fileInfo = defMap.fileInfos[virtualFile.id] ?: return null
    return fileInfo.modData
}
