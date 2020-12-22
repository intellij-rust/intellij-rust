/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.project.Project
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.shouldIndexFile
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.fileId
import org.rust.openapiext.testAssert
import org.rust.stdext.mapToSet

/**
 * Returns `null` if [crate] has null `id` or `rootMod`,
 * or if crate should not be indexed (e.g. test/bench non-workspace crate)
 */
fun buildDefMap(crate: Crate, allDependenciesDefMaps: Map<Crate, CrateDefMap>): CrateDefMap? {
    checkReadAccessAllowed()
    val project = crate.project
    check(project.isNewResolveEnabled)
    val context = CollectorContext(crate, project)
    val defMap = buildDefMapContainingExplicitItems(context, allDependenciesDefMaps) ?: return null
    DefCollector(project, defMap, context).collect()
    defMap.afterBuilt()
    testAssert({ !isCrateChanged(crate, defMap) }, { "DefMap $defMap should be up-to-date just after built" })
    return defMap
}

/** Context for [ModCollector] and [DefCollector] */
class CollectorContext(
    val crate: Crate,
    val project: Project
) {
    /** All imports (including expanded from macros - filled in [DefCollector]) */
    val imports: MutableList<Import> = mutableListOf()

    /** All macro calls */
    val macroCalls: MutableList<MacroCallInfo> = mutableListOf()
}

private fun buildDefMapContainingExplicitItems(
    context: CollectorContext,
    allDependenciesDefMaps: Map<Crate, CrateDefMap>
): CrateDefMap? {
    val crate = context.crate
    val crateId = crate.id ?: return null
    val crateRoot = crate.rootMod ?: return null

    val crateRootFile = crate.rootModFile ?: return null
    if (!shouldIndexFile(context.project, crateRootFile)) return null

    val (directDependenciesDefMaps, allDependenciesDefMapsById) =
        getDirectAndAllDependencies(crate, allDependenciesDefMaps)

    val crateRootOwnedDirectory = crateRoot.virtualFile?.parent
        ?: error("Can't find parent directory for crate root of $crate crate")
    val crateDescription = crate.toString()
    val rootModMacroIndex = allDependenciesDefMaps.values.map { it.rootModMacroIndex + 1 }.max() ?: 0
    val crateRootData = ModData(
        parent = null,
        crate = crateId,
        path = ModPath(crateId, emptyArray()),
        macroIndex = MacroIndex(intArrayOf(rootModMacroIndex)),
        isDeeplyEnabledByCfg = true,
        fileId = crateRoot.virtualFile.fileId,
        fileRelativePath = "",
        ownedDirectoryId = crateRootOwnedDirectory.fileId,
        hasMacroUse = false,
        crateDescription = crateDescription
    )
    val defMap = CrateDefMap(
        crate = crateId,
        root = crateRootData,
        directDependenciesDefMaps = directDependenciesDefMaps,
        allDependenciesDefMaps = allDependenciesDefMapsById,
        prelude = findPrelude(crate, allDependenciesDefMaps),
        metaData = CrateMetaData(crate),
        rootModMacroIndex = rootModMacroIndex,
        crateDescription = crateDescription
    )

    createExternCrateStdImport(crate, crateRoot, crateRootData)?.let {
        context.imports += it
        defMap.importExternCrateMacros(it.usePath.single())
    }
    val modCollectorContext = ModCollectorContext(defMap, crateRootData, context)
    collectFileAndCalculateHash(crateRoot, crateRootData, crateRootData.macroIndex, modCollectorContext)

    sortImports(context.imports)
    return defMap
}

private data class DependenciesDefMaps(
    val directDependenciesDefMaps: Map<String, CrateDefMap>,
    val allDependenciesDefMaps: Map<CratePersistentId, CrateDefMap>,
)

private fun getDirectAndAllDependencies(
    crate: Crate,
    allDependenciesDefMaps: Map<Crate, CrateDefMap>
): DependenciesDefMaps {
    val allDependenciesDefMapsById = allDependenciesDefMaps
        .filterKeys { it.id != null }
        .mapKeysTo(hashMapOf()) { it.key.id!! }
    val directDependenciesDefMaps = crate.dependencies
        .mapNotNull {
            val id = it.crate.id ?: return@mapNotNull null
            val defMap = allDependenciesDefMapsById[id] ?: return@mapNotNull null
            it.normName to defMap
        }
        .toMap(hashMapOf())
    return DependenciesDefMaps(directDependenciesDefMaps, allDependenciesDefMapsById)
}

/**
 * Look for the prelude.
 * If the dependency defines a prelude, we overwrite an already defined prelude.
 * This is necessary to import the "std" prelude if a crate depends on both "core" and "std".
 */
private fun findPrelude(crate: Crate, allDependenciesDefMaps: Map<Crate, CrateDefMap>): ModData? {
    /**
     * Correct prelude is always selected (core vs std), because [Crate.flatDependencies] is top sorted.
     * And we peek prelude from latest dependency.
     */
    val dependencies = crate.dependencies.mapToSet { it.crate }
    val dependenciesTopSorted = crate.flatDependencies.filter { it in dependencies }
    return dependenciesTopSorted
        .asReversed()
        .asSequence()
        .mapNotNull { allDependenciesDefMaps[it]?.prelude }
        .firstOrNull()
}

private fun createExternCrateStdImport(crate: Crate, crateRoot: RsFile, crateRootData: ModData): Import? {
    // Rust injects implicit `extern crate std` in every crate root module unless it is
    // a `#![no_std]` crate, in which case `extern crate core` is injected. However, if
    // there is a (unstable?) `#![no_core]` attribute, nothing is injected.
    //
    // https://doc.rust-lang.org/book/using-rust-without-the-standard-library.html
    // The stdlib lib itself is `#![no_std]`, and the core is `#![no_core]`
    val name = when (crateRoot.attributes) {
        RsFile.Attributes.NONE -> AutoInjectedCrates.STD
        RsFile.Attributes.NO_STD -> AutoInjectedCrates.CORE
        RsFile.Attributes.NO_CORE -> return null
    }
    return Import(
        crateRootData,
        arrayOf(name),
        nameInScope = if (crate.edition == CargoWorkspace.Edition.EDITION_2015) name else "_",
        visibility = crateRootData.visibilityInSelf,
        isExternCrate = true
    )
}

/**
 * This is a workaround for some real-project cases. See:
 * - [RsUseResolveTest.`test import adds same name as existing`]
 * - https://github.com/rust-lang/cargo/blob/875e0123259b0b6299903fe4aea0a12ecde9324f/src/cargo/util/mod.rs#L23
 */
private fun sortImports(imports: MutableList<Import>) {
    imports.sortWith(
        // TODO: Profile & optimize
        compareByDescending<Import> { it.nameInScope in it.containingMod.visibleItems }
            .thenBy { it.isGlob }
            .thenByDescending { it.containingMod.path.segments.size }  // imports from nested modules first
    )
}

private fun CrateDefMap.afterBuilt() {
    root.visitDescendants {
        it.isShadowedByOtherFile = false
    }

    // TODO: uncomment when #[cfg_attr] will be supported
    // testAssert { missedFiles.isEmpty() }
}
