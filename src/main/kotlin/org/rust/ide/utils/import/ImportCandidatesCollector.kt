/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.map2Array
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CrateGraphService
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsFile.Attributes
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.TraitImplSource
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.resolve.ref.deepResolve
import org.rust.lang.core.resolve2.*
import org.rust.stdext.mapNotNullToSet
import org.rust.stdext.mapToSet

/**
 * ## High-level description
 * Consider we have code like:
 * ```rust
 * fn main() {
 *     func();
 * }
 * ```
 * And want to find import for `func`.
 * Import will have path like `mod1::mod2::mod3::modn::func`.
 * - `mod1` is always crate root - either current crate or one of its dependencies.
 *   See [getInitialDefMapsToSearch].
 * - Given candidates for `mod{n}` we can find candidates for `mod{n+1}` among [ModData.visibleItems] for `mod{n}`.
 *   See [getAllModPaths].
 * - Finally when we have path `mod1::mod2::mod3::modn`,
 *   we filter items by name in [ModData.visibleItems] for `modn`,
 *   and receive result path `mod1::mod2::mod3::modn::func`.
 *   See [getAllItemPathsInMod].
 *
 * ### Filtration (when we find multiple paths for single item)
 * - If we can use path to crate where item is declared, then we ignore reexports in all other crates - see [filterForSingleCrate].
 * - If there are multiple paths in single crate, then we choose the shortest one - see [filterShortestPath].
 * - Private reexports are not used - see [checkVisibility].
 *
 * ### Sorting
 * We sort items in following order:
 * - Std
 * - Workspace
 * - Dependencies
 * See [ImportCandidate.compareTo].
 */
object ImportCandidatesCollector {
    fun getImportCandidates(context: ImportContext, targetName: String): List<ImportCandidate> {
        val itemsPaths = context.getAllModPaths()
            .flatMap { context.getAllItemPathsInMod(it, targetName) }
        return context.convertToCandidates(itemsPaths)
    }

    fun getCompletionCandidates(
        context: ImportContext,
        prefixMatcher: PrefixMatcher,
        processedElements: MultiMap<String, RsElement>
    ): List<ImportCandidate> {
        val modPaths = context.getAllModPaths()
        val allNames = modPaths.flatMapTo(hashSetOf()) { it.mod.visibleItems.keys }
        val nameToPriority = prefixMatcher.sortMatching(allNames)
            .withIndex().associate { (index, value) -> value to index }
        val itemsPaths = modPaths
            .flatMap { context.getAllItemPathsInMod(it, nameToPriority::containsKey) }
        return context.convertToCandidates(itemsPaths)
            /** we need this filter in addition to [hasVisibleItemInRootScope] because there can be local imports */
            .filter { it.item !in processedElements[it.itemName] }
            .sortedBy { nameToPriority[it.itemName] }
    }

    /**
     * Returns a sequence of import trait candidates for given [resolvedMethods].
     * After importing any of which it becomes possible to resolve the corresponding method call correctly.
     *
     * Returns null if there aren't traits to import at all. It can mean:
     * * given [resolvedMethods] don't refer to any trait
     * * if at least one trait related to [resolvedMethods] is already in scope
     */
    fun getImportCandidates(scope: RsElement, resolvedMethods: List<MethodResolveVariant>): List<ImportCandidate>? =
        getTraitImportCandidates(scope, resolvedMethods.map { it.source })

    fun getTraitImportCandidates(scope: RsElement, sources: List<TraitImplSource>): List<ImportCandidate>? {
        val traits = collectTraitsToImport(scope, sources)
            ?: return null
        val traitsPaths = traits.mapNotNullToSet { it.asModPath() }

        val context = ImportContext.from(scope, ImportContext.Type.AUTO_IMPORT) ?: return emptyList()
        val modPaths = context.getAllModPaths()
        val itemsPaths = modPaths.flatMap { context.getTraitsPathsInMod(it, traitsPaths) }
        return context.convertToCandidates(itemsPaths)
    }

    private fun collectTraitsToImport(scope: RsElement, sources: List<TraitImplSource>): List<RsTraitItem>? {
        val traits = sources.mapNotNull { source ->
            if (source.isInherent) return null
            source.requiredTraitInScope
        }
        return if (traits.filterInScope(scope).isNotEmpty()) null else traits
    }

    private fun getImportCandidates(context: ImportContext, target: RsQualifiedNamedElement): List<ImportCandidate> {
        val name = if (target is RsFile) {
            target.modName
        } else {
            target.name
        } ?: return emptyList()
        return getImportCandidates(context, name)
            .filter { it.item == target }
    }

    fun findImportCandidate(context: ImportContext, target: RsQualifiedNamedElement): ImportCandidate? =
        getImportCandidates(context, target).firstOrNull()
}

private fun ImportContext.convertToCandidates(itemsPaths: List<ItemUsePath>): List<ImportCandidate> =
    itemsPaths
        .filterForSingleRootItem(this)
        .groupBy { it.toItemWithNamespace() }
        .mapValues { (item, paths) -> filterForSingleItem(paths, item) }
        .flatMap { (item, paths) ->
            val itemsPsi = item
                .toPsi(rootInfo)
                .filterIsInstance<RsQualifiedNamedElement>()
                .filterByNamespace(this)
            // cartesian product of `itemsPsi` and `paths`
            itemsPsi.flatMap { itemPsi ->
                paths.map { path ->
                    val importInfo = createImportInfo(path)
                    val isRootPathResolved = isRootPathResolved(importInfo.usePath)
                    ImportCandidate(itemPsi, path.path, path.crate, importInfo, isRootPathResolved)
                }
            }
        }
        .filter { it.item !is RsTraitItem || isUsefulTraitImport(it.info.usePath) }
        // for items which belongs to multiple namespaces (e.g. unit structs)
        .distinctBy { it.item to it.info.usePath }
        .sorted()

@Suppress("ArrayInDataClass")
private data class ModUsePath(
    val path: Array<String>,
    /** corresponds to `path.first()` */
    val crate: Crate,
    /** corresponds to `path.last()` */
    val mod: ModData,
    val needExternCrate: Boolean,
) {
    override fun toString(): String = path.joinToString("::")
}

private fun ImportContext.getAllModPaths(): List<ModUsePath> {
    val defMaps = rootDefMap.getInitialDefMapsToSearch(project.crateGraph)
    val explicitCrates = defMaps.explicit.mapToSet { (_, defMap) -> defMap.crate }
    val result = mutableListOf<ModUsePath>()
    for ((crateName, defMap) in defMaps.all) {
        val filterCrate = { crate: CratePersistentId -> crate == defMap.crate || crate !in explicitCrates }
        val crate = project.crateGraph.findCrateById(defMap.crate) ?: continue
        val rootPath = ModUsePath(arrayOf(crateName), crate, defMap.root, needExternCrate = defMap.crate !in explicitCrates)
        visitVisibleModules(rootPath, filterCrate, result::add)
    }
    return result
}

/** bfs using [ModData.visibleItems] as edges */
private fun ImportContext.visitVisibleModules(
    rootPath: ModUsePath,
    filterCrate: (CratePersistentId) -> Boolean,
    processor: (ModUsePath) -> Unit
) {
    val visited = hashSetOf(rootPath.mod)
    var pathsCurrent = listOf(rootPath)
    var pathsNext = mutableListOf<ModUsePath>()
    while (pathsCurrent.isNotEmpty()) {
        for (pathCurrent in pathsCurrent) {
            processor(pathCurrent)
            for (pathNext in findPathsToModulesInScope(pathCurrent)) {
                if (filterCrate(pathNext.mod.crate) && pathNext.mod !in visited) {
                    pathsNext += pathNext
                }
            }
        }
        visited += pathsNext.map { it.mod }
        pathsCurrent = pathsNext
        pathsNext = mutableListOf()
    }
}

private fun ImportContext.findPathsToModulesInScope(path: ModUsePath): List<ModUsePath> =
    path.mod.visibleItems.mapNotNull { (name, perNs) ->
        val childMod = perNs.types.singleOrNull {
            it.isModOrEnum && checkVisibility(it, path.mod)
        } ?: return@mapNotNull null
        val childModData = rootDefMap.tryCastToModData(childMod) ?: return@mapNotNull null
        path.copy(path = path.path + name, mod = childModData)
    }

private data class InitialDefMaps(
    /**
     * Crates which are available as-is (without inserting addition `extern crate`).
     * Note that though technically `core` is available as-is,
     * it is not included in this list since `std` should be used instead.
     */
    val explicit: List<Pair<String, CrateDefMap>>,
    /** All crates which we can import from */
    val all: List<Pair<String, CrateDefMap>>,
)

/**
 * Other stdlib crates such as `proc_macro`, `test`, `unwind`
 * are available only when there is explicit `extern crate ...;`
 */
private val ADDITIONAL_STDLIB_DEPENDENCIES: Set<String> = setOf("core", "alloc")

private fun CrateDefMap.getInitialDefMapsToSearch(crateGraph: CrateGraphService): InitialDefMaps {
    val externPreludeAdjusted = if (AutoInjectedCrates.STD in externPrelude) {
        externPrelude.filterKeys { it != AutoInjectedCrates.CORE }
    } else {
        externPrelude
    }
    val dependencies = externPreludeAdjusted
        .entries.groupBy({ it.value }, { it.key })
        .mapValues { (defMap, names) ->
            // if crate is imported using `extern crate` with alias, we should use alias
            // if there are multiply aliases, we choose any of them
            names.singleOrNull() ?: names.first { it != defMap.metaData.name }
        }
        .map { (defMap, name) -> name to defMap }
    val additionalStdlibDependencies = ADDITIONAL_STDLIB_DEPENDENCIES
        .mapNotNull { name ->
            val defMap = directDependenciesDefMaps[name] ?: return@mapNotNull null
            name to defMap
        }
        .filter { (name, defMap) ->
            name !in externPreludeAdjusted
                && crateGraph.findCrateById(defMap.crate)?.origin == PackageOrigin.STDLIB
                && stdlibAttributes.canUseCrate(name)
        }
    val explicitDefMaps = listOf("crate" to this) + dependencies
    val allDefMaps = explicitDefMaps + additionalStdlibDependencies
    return InitialDefMaps(explicitDefMaps, allDefMaps)
}

private fun Attributes.canUseCrate(crate: String): Boolean =
    when (this) {
        Attributes.NONE -> true
        Attributes.NO_STD -> crate != AutoInjectedCrates.STD
        Attributes.NO_CORE -> crate != AutoInjectedCrates.STD && crate != AutoInjectedCrates.CORE
    }

/**
 * Checks that import is visible, and it is not private reexport.
 * We shouldn't use private reexports in order to not generate code like `use crate::HashSet;`.
 */
private fun ImportContext.checkVisibility(visItem: VisItem, modData: ModData): Boolean {
    val visibility = visItem.visibility
    if (!visibility.isVisibleFromMod(rootModData)) return false
    if (visibility is Visibility.Restricted) {
        val isPrivate = visibility.inMod == modData
        val isExplicitlyDeclared = !visItem.isCrateRoot && visItem.containingMod == modData.path
        if (isPrivate && !isExplicitlyDeclared) {
            Testmarks.IgnorePrivateImportInParentMod.hit()
            return false
        }
    }
    return true
}


@Suppress("ArrayInDataClass")
private data class ItemUsePath(
    val path: Array<String>,
    /** corresponds to `path.first()` */
    val crate: Crate,
    /** corresponds to `path.last()` */
    val item: VisItem,
    val namespace: Namespace,
    val needExternCrate: Boolean,
) {
    fun toItemWithNamespace(): ItemWithNamespace = ItemWithNamespace(item.path, item.isModOrEnum, namespace)

    override fun toString(): String = "${path.joinToString("::")} for ${item.path}"
}

private fun ImportContext.getAllItemPathsInMod(modPath: ModUsePath, itemNameFilter: (String) -> Boolean): Sequence<ItemUsePath> =
    modPath.mod.visibleItems
        .asSequence().filter { itemNameFilter(it.key) }
        .flatMap { (name, perNs) -> getPerNsPaths(modPath, perNs, name) }

private fun ImportContext.getAllItemPathsInMod(modPath: ModUsePath, itemName: String): List<ItemUsePath> {
    val perNs = modPath.mod.visibleItems[itemName] ?: return emptyList()
    return getPerNsPaths(modPath, perNs, itemName)
}

private fun ImportContext.getPerNsPaths(modPath: ModUsePath, perNs: PerNs, name: String): List<ItemUsePath> =
    perNs.getVisItemsByNamespace().flatMap { (visItems, namespace) ->
        visItems
            .filter {
                checkVisibility(it, modPath.mod)
                    && (type == ImportContext.Type.OTHER || !hasVisibleItemInRootScope(name, namespace))
            }
            .map { ItemUsePath(modPath.path + name, modPath.crate, it, namespace, modPath.needExternCrate) }
    }

private fun ImportContext.hasVisibleItemInRootScope(name: String, namespace: Namespace): Boolean {
    val perNs = rootDefMap.resolveNameInModule(rootModData, name, withLegacyMacros = true)
    return perNs.getVisItems(namespace).isNotEmpty()
}

/** Returns paths to [traits] in scope of [modPath] */
private fun ImportContext.getTraitsPathsInMod(modPath: ModUsePath, traits: Set<ModPath>): List<ItemUsePath> =
    modPath.mod.visibleItems
        .flatMap { (name, perNs) ->
            perNs.types
                .filter { checkVisibility(it, modPath.mod) && it.path in traits }
                .map { ItemUsePath(modPath.path + name, modPath.crate, it, Namespace.Types, modPath.needExternCrate) }
        }


private data class ItemWithNamespace(val path: ModPath, val isModOrEnum: Boolean, val namespace: Namespace) {
    override fun toString(): String = "$path ($namespace)"
}

private fun ItemWithNamespace.toPsi(info: RsModInfo): List<RsNamedElement> =
    VisItem(path, Visibility.Public, isModOrEnum).toPsi(info, namespace)

private fun filterForSingleItem(paths: List<ItemUsePath>, item: ItemWithNamespace): List<ItemUsePath> =
    filterForSingleCrate(paths, item.path.crate)
        .groupBy { it.crate }
        .mapValues { filterShortestPath(it.value) }
        .flatMap { it.value }

/**
 * If we can access crate of item ⇒ ignore paths through other crates.
 * Exception: when item is declared in `core` and reexported in `std`, we should use `std` path.
 */
private fun filterForSingleCrate(paths: List<ItemUsePath>, itemCrate: CratePersistentId): List<ItemUsePath> {
    return paths.filter { it.crate.normName == AutoInjectedCrates.STD }
        .ifEmpty {
            paths.filter { it.crate.id == itemCrate }
        }
        .ifEmpty {
            paths
        }
}

/** In each crate choose the shortest path(s) */
private fun filterShortestPath(paths: List<ItemUsePath>): List<ItemUsePath> {
    val minPathSize = paths.minOf { it.path.size }
    return paths.filter { it.path.size == minPathSize }
}

/**
 * error::Error
 * ~~~~~ this = [std::error, core::error]
 * ~~~~~~~~~~~~ root path - same item for both, so keep only `std::error`
 */
private fun List<ItemUsePath>.filterForSingleRootItem(context: ImportContext): List<ItemUsePath> {
    if (size <= 1 || !all { it.item.isModOrEnum }) return this
    if (context.type == ImportContext.Type.COMPLETION) return this
    val segments = context.pathInfo?.nextSegments ?: return this
    return groupBy { resolveRootPath(context.rootDefMap, it, segments) ?: return this }
        .mapValues { (perNs, paths) ->
            val crate = perNs.singleCrate() ?: return this
            filterForSingleCrate(paths, crate)
        }
        .flatMap { it.value }
}

private fun PerNs.singleCrate(): CratePersistentId? =
    (types + values + macros)
        .mapTo(hashSetOf()) { it.crate }
        .singleOrNull()

/**
 * mod1::mod2::Item
 *       ~~~~~~~~~~ segments
 * ~~~~ path
 */
private fun resolveRootPath(defMap: CrateDefMap, path: ItemUsePath, segments: List<String>): PerNs? {
    var perNs = PerNs.types(path.item)
    for (segment in segments) {
        val visItem = perNs.types.singleOrNull { !it.visibility.isInvisible } ?: return null
        val modData = defMap.tryCastToModData(visItem) ?: return null
        perNs = modData.getVisibleItem(segment)
    }
    return perNs
}

private fun List<RsQualifiedNamedElement>.filterByNamespace(context: ImportContext): List<RsQualifiedNamedElement> {
    val pathInfo = context.pathInfo ?: return this
    return filter {
        pathInfo.namespaceFilter(it) && checkProcMacroType(it, pathInfo.parentIsMetaItem)
    }
}

fun checkProcMacroType(element: RsQualifiedNamedElement, parentIsMetaItem: Boolean): Boolean {
    if (parentIsMetaItem) return true  /** attr and derives are checked in [RsPath.namespaceFilter] */
    return if (element is RsFunction && element.isProcMacroDef) element.isBangProcMacroDef else true
}

private fun ImportContext.isUsefulTraitImport(usePath: String): Boolean {
    if (pathInfo?.rootPathText == null) return true
    val path = createPathWithImportAdded(usePath) ?: return false
    val element = path.reference?.deepResolve() as? RsQualifiedNamedElement ?: return false

    // Looks like it's useless to access trait associated types directly (i.e. `Trait::Type`),
    // but methods can be used in UFCS and associated functions or constants can be accessed
    // it they have `Self` type in a signature
    return element !is RsAbstractable
        || element.owner !is RsAbstractableOwner.Trait
        || element.canBeAccessedByTraitName
}

private fun ImportContext.isRootPathResolved(usePath: String): Boolean {
    if (type == ImportContext.Type.COMPLETION) return true
    val path = createPathWithImportAdded(usePath) ?: return false
    return path.reference?.resolve() != null
}

private fun ImportContext.createPathWithImportAdded(usePath: String): RsPath? {
    val rootPathText = pathInfo?.rootPathText ?: return null
    val rootPathParsingMode = pathInfo.rootPathParsingMode ?: return null
    val rootPathAllowedNamespaces = pathInfo.rootPathAllowedNamespaces ?: return null
    return RsCodeFragmentFactory(project)
        .createPathInTmpMod(rootPathText, rootMod, rootPathParsingMode, rootPathAllowedNamespaces, usePath, null)
}

private fun ImportContext.createImportInfo(path: ItemUsePath): ImportInfo {
    val crateName = path.path.first()
    val segments = path.path.map2Array(String::escapeIdentifierIfNeeded)
    return if (crateName == "crate") {
        val usePath = segments.joinToString("::").let {
            if (rootDefMap.isAtLeastEdition2018) it else it.removePrefix("crate::")
        }
        ImportInfo.LocalImportInfo(usePath)
    } else {
        val needInsertExternCrateItem = path.needExternCrate
            || !rootDefMap.isAtLeastEdition2018 && !rootDefMap.hasExternCrateInCrateRoot(crateName)
        val crateRelativePath = segments.copyOfRange(1, segments.size).joinToString("::")
        ImportInfo.ExternCrateImportInfo(
            crate = path.crate,
            externCrateName = crateName,
            needInsertExternCrateItem = needInsertExternCrateItem,
            crateRelativePath = crateRelativePath,
            hasModWithSameNameAsExternCrate = crateName in rootModData.childModules
        )
    }
}

private fun CrateDefMap.hasExternCrateInCrateRoot(externCrateName: String): Boolean {
    val externDefMap = externPrelude[externCrateName] ?: return false
    return externCratesInRoot[externCrateName] == externDefMap
}

private fun RsNamedElement.asModPath(): ModPath? {
    val name = name ?: return null
    val modInfo = getModInfo(containingMod) ?: return null
    return modInfo.modData.path.append(name)
}
