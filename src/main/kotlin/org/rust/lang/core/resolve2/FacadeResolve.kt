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
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.refactoring.move.common.RsMoveUtil.containingModOrSelf
import org.rust.lang.core.completion.RsMacroCompletionProvider
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.crate.impl.CargoBasedCrate
import org.rust.lang.core.crate.impl.DoctestCrate
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS
import org.rust.lang.core.resolve.ref.RsMacroPathReferenceImpl
import org.rust.lang.core.resolve.ref.RsResolveCache
import org.rust.lang.core.resolve2.RsModInfoBase.*
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

    for ((name, perNs) in modData.visibleItems.entriesWithName(processor.name)) {
        val visItemsByNamespace = arrayOf(
            perNs.types to Namespace.Types,
            perNs.values to Namespace.Values,
            perNs.macros to Namespace.Macros,
        )
        // We need a `Set` here because item could belong to multiple namespaces (e.g. unit struct)
        // Also we need to distinguish unit struct and e.g. mod and function with same name in one module
        val elements = hashSetOf<RsNamedElement>()
        for ((visItems, namespace) in visItemsByNamespace) {
            if (namespace !in ns) continue
            for (visItem in visItems) {
                if (ipm == WITHOUT_PRIVATE_IMPORTS && visItem.visibility == Visibility.Invisible) continue
                val visibilityFilter = visItem.visibility.createFilter(project)
                for (element in visItem.toPsi(defMap, project, namespace)) {
                    if (!elements.add(element)) continue
                    if (processor(name, element, visibilityFilter)) return true
                }
            }
        }
    }

    if (processor.name == null && Namespace.Types in ns) {
        for ((traitPath, traitVisibility) in modData.unnamedTraitImports) {
            val trait = VisItem(traitPath, traitVisibility)
            val visibilityFilter = traitVisibility.createFilter(project)
            for (traitPsi in trait.toPsi(defMap, project, Namespace.Types)) {
                if (processor("_", traitPsi, visibilityFilter)) return true
            }
        }
    }

    if (ipm.withExternCrates && Namespace.Types in ns) {
        for ((name, externCrateDefMap) in defMap.externPrelude.entriesWithName(processor.name)) {
            val existingItemInScope = modData.visibleItems[name]
            if (existingItemInScope != null && existingItemInScope.types.isNotEmpty()) continue

            val externCrateRoot = externCrateDefMap.root.toRsMod(project)
                // crate root can't multiresolve
                .singleOrNull()
                ?: continue
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
    isAttrOrDerive: Boolean,
    runBeforeResolve: (() -> Boolean)? = null
): Boolean? {
    val (project, defMap, modData, crate) = when (val info = getModInfo(scope)) {
        is CantUseNewResolve -> return null
        InfoNotFound -> return false
        is RsModInfo -> info
    }
    if (runBeforeResolve != null && runBeforeResolve()) return true

    return modData.processMacros(macroPath, isAttrOrDerive, processor, defMap, crate, project)
}

private fun ModData.processMacros(
    /** null if path is qualified */
    macroPath: PsiElement?,
    isAttrOrDerive: Boolean,
    processor: RsResolveProcessor,
    defMap: CrateDefMap,
    crate: Crate,
    project: Project,
): Boolean {
    val isQualified = macroPath == null || macroPath is RsPath && macroPath.qualifier != null
    if (!isQualified) {
        check(macroPath != null)
        val macroIndex = getMacroIndex(macroPath, defMap, crate)
        for ((name, macroInfos) in legacyMacros.entriesWithName(processor.name)) {
            val macroInfo = if (!isAttrOrDerive) {
                filterMacrosByIndex(macroInfos, macroIndex)
            } else {
                macroInfos.lastOrNull { it is ProcMacroDefInfo }
            } ?: continue
            val visItem = VisItem(macroInfo.path, Visibility.Public)
            if (visibleItems[name]?.macros?.any { it.path == visItem.path } == true) {
                // macros will be handled in [visibleItems] loop
                continue
            }
            val macroContainingMod = visItem.containingMod.toRsMod(defMap, project).singleOrNull() ?: continue
            val macroDefMap = defMap.getDefMap(macroInfo.crate) ?: continue
            val macroCrate = project.crateGraph.findCrateById(macroInfo.crate) ?: continue
            val macro = macroInfo.legacyMacroToPsi(macroContainingMod, macroDefMap, macroCrate) ?: continue
            if (processor(name, macro)) return true
        }
    }

    for ((name, perNs) in visibleItems.entriesWithName(processor.name)) {
        for (visItem in perNs.macros) {
            val macro = visItem.scopedMacroToPsi(defMap, project) ?: continue
            val visibilityFilter = visItem.visibility.createFilter(project)
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

fun <T> RsMacroCall.resolveToMacroUsingNewResolveAndThen(
    withNewResolve: (RsNamedElement?) -> T,
    withoutNewResolve: () -> T
): T? =
    resolveToMacroAndThen(withoutNewResolve) { defInfo, info ->
        val visItem = VisItem(defInfo.path, Visibility.Public)
        val def = visItem.scopedMacroToPsi(info.defMap, info.project)
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
fun RsMacroCall.resolveToMacroWithoutPsi(): RsMacroDataWithHash<*>? =
    resolveToMacroAndThen(
        withNewResolve = { def, _ -> RsMacroDataWithHash.fromDefInfo(def) },
        withoutNewResolve = {
            val psi = path.reference?.resolve() as? RsNamedElement
            psi?.let { RsMacroDataWithHash.fromPsi(it) }
        }
    )

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
        val crate = project.crateGraph.findCrateById(def.crate) ?: return@resolveToMacroAndThen null
        defMap.root.processMacros(
            macroPath = null,  // null because we resolve qualified macro path
            isAttrOrDerive = false,
            processor = processor,
            defMap = defMap,
            crate = crate,
            project = project
        )
    }

/**
 * If new resolve can be used, computes result using [withNewResolve].
 * Otherwise fallbacks to [withoutNewResolve].
 */
private fun <T> RsMacroCall.resolveToMacroAndThen(
    withoutNewResolve: () -> T?,
    withNewResolve: (MacroDefInfo, RsModInfo) -> T?
): T? {
    val info = when {
        !project.isNewResolveEnabled -> CantUseNewResolve("not enabled")
        isTopLevelExpansion || path.qualifier != null -> getModInfo(containingMod)
        else -> CantUseNewResolve("not top level")
    }
    return when (info) {
        is CantUseNewResolve -> withoutNewResolve()
        InfoNotFound -> null
        is RsModInfo -> {
            val def = resolveToMacroDefInfo(info) ?: return null
            withNewResolve(def, info)
        }
    }
}

fun RsMetaItem.resolveToProcMacroWithoutPsi(): ProcMacroDefInfo? {
    val resolvedMacro = resolveToProcMacroWithoutPsiWOHC(check = true) ?: return null
    if (resolvedMacro.isHardcodedNotAMacro) return null
    return resolvedMacro
}

/**
 * TODO rename. WOHC = without hardcoded check.
 *   rename `check`
 */
fun RsMetaItem.resolveToProcMacroWithoutPsiWOHC(check: Boolean): ProcMacroDefInfo? {
    val owner = owner ?: return null

    val info = when {
        !project.isNewResolveEnabled -> CantUseNewResolve("not enabled")
        RsProcMacroPsiUtil.canBeProcMacroCall(this) -> getModInfo(owner.containingMod)
        else -> CantUseNewResolve("not a proc macro")
    } as? RsModInfo ?: return null

    if (check) {
        val ok = when (val attr = ProcMacroAttribute.getProcMacroAttributeRaw(owner)) {
            is ProcMacroAttribute.Attr -> attr.attr == this
            ProcMacroAttribute.Derive -> RsProcMacroPsiUtil.canBeCustomDerive(this)
            ProcMacroAttribute.None -> false
        }
        if (!ok) return null
    }

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
            val macroIndex = getMacroIndex(this, defMap, crate) ?: return@resolveWithCaching null
            defMap.resolveMacroCallToMacroDefInfo(modData, callPath, macroIndex)
        }
}

private fun getMacroIndex(element: PsiElement, defMap: CrateDefMap, crate: Crate): MacroIndex? {
    val itemAndCallExpandedFrom = element.stubAncestors
        .filterIsInstance<RsExpandedElement>()
        .mapNotNull { it to (it.expandedOrIncludedFrom ?: return@mapNotNull null) }
        .firstOrNull()
    if (itemAndCallExpandedFrom == null) {
        if (element.containingFile is RsCodeFragment) return MacroIndex(intArrayOf(Int.MAX_VALUE))
        val (item, parent) = element.ancestorPairs.first { (_, parent) -> parent is RsMod }
        val modData = defMap.getModData(parent as RsMod) ?: return null
        val indexInParent = getMacroIndexInParent(item, parent, crate)
        return modData.macroIndex.append(indexInParent)
    } else {
        val (item, callExpandedFrom) = itemAndCallExpandedFrom
        val parent = item.parent
        // TODO: Possible optimization - store macro index in [resolveToMacroDefInfo] cache
        val parentIndex = getMacroIndex(callExpandedFrom, defMap, crate) ?: return null
        if (parent !is RsMod) return parentIndex  // not top level expansion
        val indexInParent = getMacroIndexInParent(item, parent, crate)
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
                    expandedFrom.hasMacroExportLocalInnerMacros to crateId
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

private sealed class RsModInfoBase {
    /** [reason] is only for debug */
    class CantUseNewResolve(val reason: String) : RsModInfoBase()
    object InfoNotFound : RsModInfoBase()
    data class RsModInfo(
        val project: Project,
        val defMap: CrateDefMap,
        val modData: ModData,
        val crate: Crate
    ) : RsModInfoBase()
}

private fun getModInfo(scope: RsMod): RsModInfoBase {
    val project = scope.project
    if (!project.isNewResolveEnabled) return CantUseNewResolve("not enabled")
    if (scope is RsModItem && scope.modName == TMP_MOD_NAME) return CantUseNewResolve("__tmp__ mod")
    if (scope.isLocal) return CantUseNewResolve("local mod")
    val crate = scope.containingCrate as? CargoBasedCrate ?: return CantUseNewResolve("not CargoBasedCrate")
    if (crate.rootModFile != null && !shouldIndexFile(project, crate.rootModFile)) {
        return CantUseNewResolve("crate root isn't indexed")
    }

    val defMap = project.getDefMap(crate) ?: return InfoNotFound
    val modData = defMap.getModData(scope) ?: return InfoNotFound

    if (isModShadowedByOtherMod(scope, modData, crate)) return CantUseNewResolve("mod shadowed by other mod")

    return RsModInfo(project, defMap, modData, crate)
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
private fun Visibility.createFilter(project: Project): (RsElement) -> VisibilityStatus {
    if (this is Visibility.Restricted) {
        val inMod = inMod.toRsMod(project)
        if (inMod.isNotEmpty()) {
            return { context ->
                val modOpenedInEditor = context.containingModOrSelf
                val visible = inMod.any(modOpenedInEditor.superMods::contains)
                if (visible) VisibilityStatus.Visible else VisibilityStatus.Invisible
            }
        }
    }
    // cfg-disabled items should resolves only from cfg-disabled modules
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

private fun VisItem.scopedMacroToPsi(defMap: CrateDefMap, project: Project): RsNamedElement? {
    val containingMod = containingMod.toRsMod(defMap, project).singleOrNull() ?: return null
    return scopedMacroToPsi(containingMod)
}

private fun VisItem.scopedMacroToPsi(containingMod: RsMod): RsNamedElement? {
    val items = containingMod.expandedItemsCached
    val legacyMacros = items.legacyMacros
        .filter { it.name == name && matchesIsEnabledByCfg(it, this) }
    if (legacyMacros.isNotEmpty()) return legacyMacros.singlePublicOrFirst()

    if (name !in KNOWN_DERIVABLE_TRAITS || containingMod.containingCrate?.origin != PackageOrigin.STDLIB) {
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

private fun MacroDefInfo.legacyMacroToPsi(containingMod: RsMod, defMap: CrateDefMap, crate: Crate): RsElement? {
    val items = containingMod.expandedItemsCached
    return when (this) {
        is DeclMacroDefInfo -> items.legacyMacros.singleOrNull {
            val defIndex = getMacroIndex(it, defMap, crate) ?: return@singleOrNull false
            MacroIndex.equals(defIndex, macroIndex)
        }
        is ProcMacroDefInfo, is DeclMacro2DefInfo -> items.named[path.name]?.firstOrNull()
    }
    // Note that we can return null, e.g. if old macro engine is enabled and macro definition is itself expanded
}

private fun VisItem.toPsi(defMap: CrateDefMap, project: Project, ns: Namespace): List<RsNamedElement> {
    if (isModOrEnum) return path.toRsModOrEnum(defMap, project)

    val containingModData = defMap.getModData(containingMod) ?: return emptyList()
    return if (containingModData.isEnum) {
        val containingEnums = containingModData.toRsEnum(project)
        containingEnums.flatMap { containingEnum ->
            containingEnum.variants
                .filter { it.name == name && ns in it.namespaces && matchesIsEnabledByCfg(it, this) }
        }
    } else {
        val containingMods = containingModData.toRsMod(project)
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

private fun ModPath.toRsMod(defMap: CrateDefMap, project: Project): List<RsMod> {
    val modData = defMap.getModData(this)
    if (modData == null || modData.isEnum) return emptyList()
    return modData.toRsMod(project)
}

private fun ModPath.toRsModOrEnum(defMap: CrateDefMap, project: Project): List<RsNamedElement /* RsMod or RsEnumItem */> {
    val modData = defMap.getModData(this) ?: return emptyList()
    return if (modData.isEnum) {
        modData.toRsEnum(project)
    } else {
        modData.toRsMod(project)
    }
}

private fun ModData.toRsEnum(project: Project): List<RsEnumItem> {
    if (!isEnum || parent == null) return emptyList()
    val containingMods = parent.toRsMod(project)
    val visItem = asVisItem()
    return containingMods.flatMap { containingMod ->
        containingMod
            .getExpandedItemsWithName<RsEnumItem>(name)
            .filter { matchesIsEnabledByCfg(it, visItem) }
    }
}

fun ModData.toRsMod(project: Project): List<RsMod> = toRsModNullable(project)
    .also {
        if (it.isEmpty()) {
            RESOLVE_LOG.warn("Can't find RsMod for $this")
        }
    }

private fun ModData.toRsModNullable(project: Project): List<RsMod> {
    if (isEnum) return emptyList()
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
