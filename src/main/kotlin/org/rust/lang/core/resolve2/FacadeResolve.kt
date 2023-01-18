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
import org.jetbrains.annotations.VisibleForTesting
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.crate.impl.CargoBasedCrate
import org.rust.lang.core.crate.impl.DoctestCrate
import org.rust.lang.core.crate.impl.FakeDetachedCrate
import org.rust.lang.core.crate.impl.FakeInvalidCrate
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.macros.errors.ResolveMacroWithoutPsiError
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS
import org.rust.lang.core.resolve.ref.ResolveCacheDependency
import org.rust.lang.core.resolve.ref.RsResolveCache
import org.rust.openapiext.testAssert
import org.rust.openapiext.toPsiFile
import org.rust.stdext.RsResult

fun processItemDeclarations(
    scope: RsItemsOwner,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    ipm: ItemProcessingMode
): Boolean {
    val info = getModInfo(scope) ?: return false
    return processItemDeclarationsUsingModInfo(scopeIsMod = scope is RsMod, info, ns, processor, ipm)
}

fun processItemDeclarationsUsingModInfo(
    scopeIsMod: Boolean,
    info: RsModInfo,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    ipm: ItemProcessingMode
): Boolean {
    val (_, defMap, modData) = info
    for ((name, perNs) in modData.visibleItems.entriesWithNames(processor.names)) {
        // We need a `Set` here because item could belong to multiple namespaces (e.g. unit struct)
        // Also we need to distinguish unit struct and e.g. mod and function with same name in one module
        val elements = hashSetOf<RsNamedElement>()
        for ((visItems, namespace) in perNs.getVisItemsByNamespace()) {
            if (namespace !in ns) continue
            for (visItem in visItems) {
                if (ipm == WITHOUT_PRIVATE_IMPORTS && visItem.visibility == Visibility.Invisible) continue
                if (namespace == Namespace.Types && visItem.visibility.isInvisible && name in defMap.externPrelude) continue
                val visibilityFilter = visItem.visibility.createFilter()
                for (element in visItem.toPsi(info, namespace)) {
                    if (!elements.add(element)) continue
                    if (processor.process(name, element, visibilityFilter)) return true
                }
            }
        }
    }

    if (processor.names == null && Namespace.Types in ns) {
        for ((traitPath, traitVisibility) in modData.unnamedTraitImports) {
            val trait = VisItem(traitPath, traitVisibility)
            val visibilityFilter = traitVisibility.createFilter()
            for (traitPsi in trait.toPsi(info, Namespace.Types)) {
                if (processor.process("_", traitPsi, visibilityFilter)) return true
            }
        }
    }

    if (ipm.withExternCrates && Namespace.Types in ns && scopeIsMod) {
        for ((name, externCrateDefMap) in defMap.externPrelude.entriesWithNames(processor.names)) {
            val existingItemInScope = modData.visibleItems[name]
            if (existingItemInScope != null && existingItemInScope.types.any { !it.visibility.isInvisible }) continue

            val externCrateRoot = externCrateDefMap.rootAsRsMod(info.project) ?: continue
            processor.process(name, externCrateRoot) && return true
        }
    }

    return false
}

fun processMacros(
    scope: RsItemsOwner,
    processor: RsResolveProcessor,
    /** Needed to filter textual scoped macros if path is unqualified. */
    macroPath: RsPath,
): Boolean {
    val info = getModInfo(scope) ?: return false
    return info.modData.processMacros(macroPath, processor, info)
}

private fun ModData.processMacros(
    macroPath: RsPath,
    processor: RsResolveProcessor,
    info: RsModInfo,
): Boolean {
    val isQualified = macroPath.qualifier != null
    val isAttrOrDerive = macroPath.parent is RsMetaItem

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
        val macroIndex = info.getMacroIndex(macroPath, info.crate)
        for ((name, macroInfos) in legacyMacros.entriesWithNames(processor.names)) {
            val macroInfo = if (!isAttrOrDerive) {
                filterMacrosByIndex(macroInfos, macroIndex)
            } else {
                macroInfos.lastOrNull { it is ProcMacroDefInfo }
            } ?: continue
            val visItem = VisItem(macroInfo.path, Visibility.Public)
            val macroContainingScope = visItem.containingMod.toScope(info).singleOrNull() ?: continue
            val macro = macroInfo.legacyMacroToPsi(macroContainingScope, info) ?: continue
            if (processor.process(name, macro)) return true
        }

        if (!isHanging) {
            info.defMap.prelude?.let { prelude ->
                if (prelude.processScopedMacros(processor, info)) return true
            }
        }
    }

    return false
}

private fun ModData.processScopedMacros(
    processor: RsResolveProcessor,
    info: RsModInfo,
    filter: (name: String) -> Boolean = { true },
): Boolean {
    for ((name, perNs) in visibleItems.entriesWithNames(processor.names)) {
        for (visItem in perNs.macros) {
            if (!filter(name)) continue
            val macro = visItem.scopedMacroToPsi(info) ?: continue
            val visibilityFilter = visItem.visibility.createFilter()
            if (processor.process(name, macro, visibilityFilter)) return true
        }
    }
    return false
}

private fun filterMacrosByIndex(macroInfos: List<MacroDefInfo>, macroIndex: MacroIndex?): MacroDefInfo? =
    when {
        macroIndex != null -> macroInfos.getLastBefore(macroIndex)
        else -> macroInfos.last()  // this is kind of error, can choose anything here
    }

fun RsPossibleMacroCall.resolveToMacroUsingNewResolve(): RsNamedElement? {
    val (defInfo, info) = resolveToMacroInfo() ?: return null
    val visItem = VisItem(defInfo.path, Visibility.Public)
    return visItem.scopedMacroToPsi(info)
}

/**
 * Resolve without PSI is needed to prevent caching incomplete result in [expandedItemsCached].
 * Consider:
 * - Macro expansion task wants to expand some macro
 * - Firstly we resolve macro path
 * - It can trigger items resolve [processItemDeclarations].
 *   E.g. if macro path is two segment - we need to resolve first segment as mod
 * - [processItemDeclarations] uses [expandedItemsCached] which will try to expand all macros in scope,
 *   which results in recursion,
 *   which is prevented by returning null from macro expansion,
 *   therefore result of [expandedItemsCached] is incomplete (and cached)
 */
fun RsMacroCall.resolveToMacroWithoutPsi(): RsResult<RsMacroDataWithHash<*>, ResolveMacroWithoutPsiError> {
    val (def, _) = resolveToMacroInfo()
        ?: return RsResult.Err(ResolveMacroWithoutPsiError.Unresolved)
    return RsMacroDataWithHash.fromDefInfo(def)
}

/** See [resolveToMacroWithoutPsi] */
fun RsMacroCall.resolveToMacroAndGetContainingCrate(): Crate? {
    val (def, _) = resolveToMacroInfo() ?: return null
    return project.crateGraph.findCrateById(def.crate)
}

/** See [resolveToMacroWithoutPsi] */
fun RsMacroCall.resolveToMacroAndProcessLocalInnerMacros(processor: RsResolveProcessor): Boolean? {
    val (def, info) = resolveToMacroInfo() ?: return null
    if (def !is DeclMacroDefInfo || !def.hasLocalInnerMacros) return null
    val project = info.project
    val defMap = project.defMapService.getOrUpdateIfNeeded(def.crate) ?: return null
    return defMap.root.processMacros(path, processor, info)
}

private fun RsPossibleMacroCall.resolveToMacroInfo(): Pair<MacroDefInfo, RsModInfo>? {
    val scope = contextStrict<RsItemsOwner>() ?: return null
    val info = getNearestAncestorModInfo(scope) ?: return null
    val def = when (val kind = kind) {
        is RsPossibleMacroCallKind.MacroCall -> kind.call.resolveToMacroDefInfo(info)
        is RsPossibleMacroCallKind.MetaItem -> kind.meta.resolveToProcMacroWithoutPsi()
    } ?: return null
    return def to info
}

fun RsMetaItem.resolveToProcMacroWithoutPsi(checkIsMacroAttr: Boolean = true): ProcMacroDefInfo? {
    val owner = owner as? RsAttrProcMacroOwner ?: return null

    if (!RsProcMacroPsiUtil.canBeProcMacroCall(this)) return null
    val info = getModInfo(owner.containingMod) ?: return null

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
        .resolveWithCaching(this, ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE) {
            val callPath = path.pathSegmentsAdjusted?.toTypedArray() ?: return@resolveWithCaching null
            val macroIndex = containingModInfo.getMacroIndex(this, crate) ?: return@resolveWithCaching null
            defMap.resolveMacroCallToMacroDefInfo(modData, callPath, macroIndex)
                ?.takeIf { it !is ProcMacroDefInfo || it.procMacroKind == RsProcMacroKind.FUNCTION_LIKE }
        }
}

@VisibleForTesting
fun RsModInfo.getMacroIndex(element: PsiElement, elementCrate: Crate): MacroIndex? {
    if (element is RsMetaItem) {
        val owner = element.owner as? RsAttrProcMacroOwner ?: return null
        val ownerIndex = getMacroIndex(owner, elementCrate) ?: return null
        val attr = ProcMacroAttribute.getProcMacroAttributeWithoutResolve(
            owner,
            explicitCrate = crate,
            withDerives = true
        )
        return when (attr) {
            is ProcMacroAttribute.Derive -> ownerIndex.append(attr.derives.indexOf(element))
            is ProcMacroAttribute.Attr -> ownerIndex
            is ProcMacroAttribute.None -> return null
        }
    }

    for ((current, parent) in element.ancestorPairs) {
        // TODO: Optimization: find [expandedOrIncludedFrom] using [parent] when it is [RsFile]
        val expandedOrIncludedFrom = (current as? RsExpandedElement)?.expandedOrIncludedFrom
        val parentIndex = when {
            expandedOrIncludedFrom != null -> getMacroIndex(expandedOrIncludedFrom, elementCrate)
            parent is RsCodeFragment -> MacroIndex(intArrayOf(Int.MAX_VALUE))
            parent is RsBlock -> {
                val parentData = dataPsiHelper?.psiToData(parent) ?: continue
                parentData.macroIndex
            }
            parent is RsMod -> run {
                val modData = getModData(parent, elementCrate.id ?: return null)
                modData?.macroIndex
            }
            else -> continue
        } ?: return null
        val indexInParent = getMacroIndexInParent(current, parent)
        return parentIndex.append(indexInParent)
    }
    return null
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

fun PsiElement.findItemWithMacroIndex(macroIndexInParent: Int): PsiElement {
    val parentStub = if (this is PsiFileBase) greenStub else (this as? StubBasedPsiElement<*>)?.greenStub
    return if (parentStub != null) {
        parentStub.childrenStubs.asSequence()
            .filter { it.hasMacroIndex() }
            .elementAt(macroIndexInParent)
            .psi
    } else {
        children.asSequence()
            .filter { it.hasMacroIndex() }
            .elementAt(macroIndexInParent)
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
        val info = getModInfo(callExpandedFrom.containingMod) ?: return segments
        val def = callExpandedFrom.resolveToMacroDefInfo(info) ?: return segments
        val defExpandedFromHasLocalInnerMacros = def is DeclMacroDefInfo && def.hasLocalInnerMacros
        return when {
            segments.size == 1 && !cameFromMacroCall() && defExpandedFromHasLocalInnerMacros -> {
                listOf(MACRO_DOLLAR_CRATE_IDENTIFIER, def.crate.toString()) + segments
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

data class RsModInfo(
    val project: Project,
    val defMap: CrateDefMap,
    val modData: ModData,
    val crate: Crate,
    val dataPsiHelper: DataPsiHelper? = null,
)

interface DataPsiHelper {
    fun psiToData(scope: RsItemsOwner): ModData?
    fun dataToPsi(data: ModData): RsItemsOwner?
    fun findModData(path: ModPath): ModData? = null
}

/** See also [getNearestAncestorModInfo] */
fun getModInfo(scope0: RsItemsOwner): RsModInfo? {
    val scope = scope0.originalElement as? RsItemsOwner ?: scope0
    if (scope !is RsMod) return getHangingModInfo(scope)
    val project = scope.project
    if (scope is RsModItem) {
        if (scope.modName == TMP_MOD_NAME) return getTmpModInfo(scope)
        if (scope.isLocal) return getLocalModInfo(scope)
    }
    val crate = when (val crate = scope.containingCrate) {
        is CargoBasedCrate -> crate
        is DoctestCrate -> return project.getDoctestModInfo(scope, crate)
        is FakeDetachedCrate -> return project.getDetachedModInfo(scope, crate)
        is FakeInvalidCrate -> return null
        else -> error("unreachable")
    }
    testAssert { crate.rootModFile == null || shouldIndexFile(project, crate.rootModFile) }

    val defMap = project.getDefMap(crate) ?: return null
    val modData = defMap.getModData(scope) ?: return null

    if (isModShadowedByOtherMod(scope, modData, crate)) {
        val contextInfo = RsModInfo(project, defMap, modData.parent ?: return null, crate)
        return getHangingModInfo(scope, contextInfo)
    }

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

private fun RsModInfo.getModData(mod: RsMod, modCrate: CratePersistentId): ModData? {
    dataPsiHelper?.psiToData(mod)?.let { return it }
    return defMap.getDefMap(modCrate)?.getModData(mod)
}

/** E.g. `fn func() { mod foo { ... } }` */
private val RsMod.isLocal: Boolean
    get() = ancestorStrict<RsBlock>() != null

/** "shadowed by other mod" means that [ModData] is not accessible from [CrateDefMap.root] through [ModData.childModules] */
private fun isModShadowedByOtherMod(mod: RsMod, modData: ModData, crate: Crate): Boolean {
    return if (mod is RsFile) {
        modData.isShadowedByOtherFile
    } else {
        // shadowed by other inline mod
        val isDeeplyEnabledByCfg = (mod.containingFile as RsFile).isDeeplyEnabledByCfg && mod.isEnabledByCfg(crate)
        isDeeplyEnabledByCfg != modData.isDeeplyEnabledByCfg
    }
}

fun <T> Map<String, T>.entriesWithNames(names: Set<String>?): Map<String, T> {
    return if (names.isNullOrEmpty()) {
        this
    } else if (names.size == 1) {
        val single = names.single()
        val value = this[single] ?: return emptyMap()
        mapOf(single to value)
    } else {
        names.mapNotNull { name -> this[name]?.let { name to it } }.toMap()
    }
}

/** Creates filter which determines whether item with [this] visibility is visible from specific [RsMod] */
private fun Visibility.createFilter(): VisibilityFilter {
    if (this is Visibility.Restricted) {
        val inMod = inMod
        if (!inMod.isHanging) {
            return fun(context: RsElement, lazyModInfo: Lazy<RsModInfo?>?): VisibilityStatus {
                val modOpenedInEditor = if (lazyModInfo != null) {
                    lazyModInfo.value
                } else {
                    getModInfo(context.containingModOrSelf)
                } ?: return VisibilityStatus.Visible
                val visible = modOpenedInEditor.modData.parents.contains(inMod)
                return if (visible) VisibilityStatus.Visible else VisibilityStatus.Invisible
            }
        }
    }
    // cfg-disabled items should resolve only from cfg-disabled modules
    if (this === Visibility.CfgDisabled) {
        return fun(context: RsElement, _: Lazy<RsModInfo?>?): VisibilityStatus {
            val file = context.containingFile as? RsFile ?: return VisibilityStatus.Visible
            // ignore doc test crates
            val crate = file.containingCrate as? CargoBasedCrate ?: return VisibilityStatus.Visible
            val isDeeplyEnabledByCfg = context.isEnabledByCfg(crate) && file.isDeeplyEnabledByCfg
            return if (isDeeplyEnabledByCfg) VisibilityStatus.CfgDisabled else VisibilityStatus.Visible
        }
    }
    return { _, _ -> VisibilityStatus.Visible }
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

    if (name !in KNOWN_DERIVABLE_TRAITS || containingScope.containingCrate.origin != PackageOrigin.STDLIB) {
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
            // Note that if macro indices are equal, the result macro is cfg-enabled
            // since cfg-enabled and cfg-disabled macros have different macro indices
            MacroIndex.equals(defIndex, macroIndex)
        }
        is ProcMacroDefInfo -> items.named[path.name]?.firstOrNull { it is RsFunction }
        is DeclMacro2DefInfo -> items.named[path.name]?.firstOrNull { it is RsMacro2 }
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
                .filter { it.name == name && ns in ENUM_VARIANT_NS && matchesIsEnabledByCfg(it, this) }
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

fun CrateDefMap.rootAsRsMod(project: Project): RsMod? = root.toRsMod(project).singleOrNull()

private inline fun <reified T : RsNamedElement> RsItemsOwner.getExpandedItemsWithName(name: String): List<T> =
    expandedItemsCached.named[name]?.filterIsInstance<T>() ?: emptyList()

fun findFileInclusionPointsFor(file: RsFile): List<FileInclusionPoint> {
    val project = file.project
    val defMapService = project.defMapService
    val virtualFile = file.virtualFile ?: return emptyList()
    if (virtualFile !is VirtualFileWithId) return emptyList()

    if (!defMapService.areAllDefMapsUpToDate()) {
        // Ensure def maps are up-to-date (`findCrates` may return an old crate if def maps haven't updated)
        val pkg = project.cargoProjects.findPackageForFile(virtualFile)
        if (pkg != null) {
            val crateGraph = project.crateGraph
            val crateIds = pkg.targets.asSequence()
                .mapNotNull { it.crateRoot }
                .mapNotNull { crateGraph.findCrateByRootMod(it)?.id }
                .toList()
            defMapService.getOrUpdateIfNeeded(crateIds)
        } else {
            // A file outside a cargo package can still be included by an `include!()` macro or `#[path = ]` attribute
            NameResolutionTestmarks.UpdateDefMapsForAllCratesWhenFindingModData.hit()
            defMapService.updateDefMapForAllCrates()
        }
    }

    val rawList = defMapService
        .findCrates(file)
        .mapNotNull { crateId ->
            val defMap = defMapService.getOrUpdateIfNeeded(crateId) ?: return@mapNotNull null
            val fileInfo = defMap.fileInfos[virtualFile.id] ?: return@mapNotNull null
            FileInclusionPoint(defMap, fileInfo.modData, fileInfo.includeMacroIndex)
        }

    return if (rawList.size == 1) {
        rawList
    } else {
        rawList.filter { it.modData.isDeeplyEnabledByCfg }.ifEmpty { rawList }
    }
}

data class FileInclusionPoint(
    val defMap: CrateDefMap,
    val modData: ModData,
    /** Non-null if the file is included via `include!` macro */
    val includeMacroIndex: MacroIndex?
)
