/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.project.Project
import org.rust.cargo.project.workspace.CargoWorkspace.Edition.EDITION_2015
import org.rust.cargo.project.workspace.CargoWorkspace.Edition.EDITION_2018
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.isEnabledByCfgSelf
import org.rust.lang.core.psi.shouldIndexFile
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.fileId
import org.rust.openapiext.testAssert

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
    val rootModMacroIndex = allDependenciesDefMaps.values.map { it.rootModMacroIndex + 1 }.maxOrNull() ?: 0
    val crateRootData = ModData(
        parent = null,
        crate = crateId,
        path = ModPath(crateId, emptyArray()),
        macroIndex = MacroIndex(intArrayOf(rootModMacroIndex)),
        isDeeplyEnabledByCfgOuter = true,
        isEnabledByCfgInner = crateRoot.isEnabledByCfgSelf(crate),
        fileId = crateRoot.virtualFile.fileId,
        fileRelativePath = "",
        ownedDirectoryId = crateRootOwnedDirectory.fileId,
        hasPathAttribute = false,
        hasMacroUse = false,
        crateDescription = crateDescription
    )
    val defMap = CrateDefMap(
        crate = crateId,
        root = crateRootData,
        directDependenciesDefMaps = directDependenciesDefMaps,
        allDependenciesDefMaps = allDependenciesDefMapsById,
        metaData = CrateMetaData(crate),
        rootModMacroIndex = rootModMacroIndex,
        stdlibAttributes = crateRoot.getStdlibAttributes(crate),
        crateDescription = crateDescription
    )

    injectPrelude(defMap)
    createExternCrateStdImport(defMap)?.let {
        context.imports += it
        defMap.importExternCrateMacros(it.usePath.single())
    }
    val modCollectorContext = ModCollectorContext(defMap, crateRootData, context)
    collectFileAndCalculateHash(crateRoot, crateRootData, crateRootData.macroIndex, modCollectorContext)

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
 * Finds prelude based on edition and root module attributes such as ```#[no_std]```.
 * Prelude for current crate can be overwritten by ```#[prelude_import]```.
 * See also:
 * - https://github.com/rust-lang/rust/blob/master/compiler/rustc_builtin_macros/src/standard_library_imports.rs
 * - https://github.com/rust-lang/rust/pull/82217
 */
private fun injectPrelude(defMap: CrateDefMap) {
    val preludeCrate = when (defMap.stdlibAttributes) {
        RsFile.Attributes.NONE -> AutoInjectedCrates.STD
        RsFile.Attributes.NO_STD -> AutoInjectedCrates.CORE
        RsFile.Attributes.NO_CORE -> return
    }
    val preludeName = when (val edition = defMap.metaData.edition) {
        // BACKCOMPAT: Rust 1.55.0. Always use "rust_$edition"
        // We don't use "rust_2015" and "rust_2018" in order to be compatible with old rustc
        EDITION_2015, EDITION_2018 -> "v1"
        else -> "rust_${edition.presentation}"
    }
    val path = arrayOf("" /* absolute path */, preludeCrate, "prelude", preludeName)
    val result = defMap.resolvePathFp(defMap.root, path, ResolveMode.IMPORT, withInvisibleItems = false)
    val resultItem = result.resolvedDef.types.singleOrNull() ?: return
    defMap.prelude = defMap.tryCastToModData(resultItem)
}

private fun createExternCrateStdImport(defMap: CrateDefMap): Import? {
    // Rust injects implicit `extern crate std` in every crate root module unless it is
    // a `#![no_std]` crate, in which case `extern crate core` is injected. However, if
    // there is a (unstable?) `#![no_core]` attribute, nothing is injected.
    //
    // https://doc.rust-lang.org/book/using-rust-without-the-standard-library.html
    // The stdlib lib itself is `#![no_std]`, and the core is `#![no_core]`
    val name = when (defMap.stdlibAttributes) {
        RsFile.Attributes.NONE -> AutoInjectedCrates.STD
        RsFile.Attributes.NO_STD -> AutoInjectedCrates.CORE
        RsFile.Attributes.NO_CORE -> return null
    }
    return Import(
        defMap.root,
        arrayOf(name),
        nameInScope = if (defMap.metaData.edition == EDITION_2015) name else "_",
        visibility = defMap.root.visibilityInSelf,
        isExternCrate = true
    )
}

private fun CrateDefMap.afterBuilt() {
    root.visitDescendants {
        it.isShadowedByOtherFile = false
    }

    // TODO: uncomment when #[cfg_attr] will be supported
    // testAssert { missedFiles.isEmpty() }
}
