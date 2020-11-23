/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.MACRO_DOLLAR_CRATE_IDENTIFIER

enum class ResolveMode { IMPORT, OTHER }

/** Returns `reachedFixedPoint=true` if we are sure that additions to [ModData.visibleItems] wouldn't change the result */
fun CrateDefMap.resolvePathFp(
    containingMod: ModData,
    path: Array<String>,
    mode: ResolveMode,
    withInvisibleItems: Boolean
): ResolvePathResult {
    var (pathKind, firstSegmentIndex) = getPathKind(path)
    // we use PerNs and not ModData for first segment,
    // because path could be one-segment: `use crate as foo;` and `use func as func2;`
    //                                         ~~~~~ path              ~~~~ path
    val firstSegmentPerNs = when {
        pathKind is PathKind.DollarCrate -> {
            val defMap = getDefMap(pathKind.crateId) ?: error("Can't find DefMap for path ${path.joinToString("::")}")
            defMap.rootAsPerNs
        }
        pathKind == PathKind.Crate -> rootAsPerNs
        pathKind is PathKind.Super -> {
            val modData = containingMod.getNthParent(pathKind.level)
                ?: return ResolvePathResult.empty(reachedFixedPoint = true)
            if (modData == root) rootAsPerNs else modData.asPerNs()
        }
        // plain import or absolute path in 2015:
        // crate-relative with fallback to extern prelude
        // (with the simplification in https://github.com/rust-lang/rust/issues/57745)
        metaData.edition == CargoWorkspace.Edition.EDITION_2015
            && (pathKind is PathKind.Absolute || pathKind is PathKind.Plain && mode == ResolveMode.IMPORT) -> {
            val firstSegment = path[firstSegmentIndex++]
            resolveNameInCrateRootOrExternPrelude(firstSegment)
        }
        pathKind == PathKind.Absolute -> {
            val crateName = path[firstSegmentIndex++]
            externPrelude[crateName]?.rootAsPerNs
            // extern crate declarations can add to the extern prelude
                ?: return ResolvePathResult.empty(reachedFixedPoint = false)
        }
        pathKind == PathKind.Plain -> {
            val firstSegment = path[firstSegmentIndex++]
            resolveNameInModule(containingMod, firstSegment)
        }
        else -> error("unreachable")
    }

    var currentPerNs = firstSegmentPerNs
    var visitedOtherCrate = false
    for (segmentIndex in firstSegmentIndex until path.size) {
        // we still have path segments left, but the path so far
        // didn't resolve in the types namespace => no resolution
        val currentModAsVisItem = currentPerNs.types
            // TODO: It is not enough - we should also check that `it.visibility` is visible inside `sourceMod`
            ?.takeIf { withInvisibleItems || !it.visibility.isInvisible }
            ?: return ResolvePathResult.empty(reachedFixedPoint = false)

        val currentModData = tryCastToModData(currentModAsVisItem)
        // could be an inherent method call in UFCS form
        // (`Struct::method`), or some other kind of associated item
            ?: return ResolvePathResult.empty(reachedFixedPoint = true)
        if (currentModData.crate != crate) visitedOtherCrate = true

        val segment = path[segmentIndex]
        currentPerNs = currentModData.getVisibleItem(segment)
    }
    val resultPerNs = if (withInvisibleItems) currentPerNs else currentPerNs.filterVisibility { !it.isInvisible }
    return ResolvePathResult(resultPerNs, reachedFixedPoint = true, visitedOtherCrate = visitedOtherCrate)
}

fun CrateDefMap.resolveMacroCallToMacroDefInfo(containingMod: ModData, macroPath: Array<String>): MacroDefInfo? {
    val perNs = resolvePathFp(
        containingMod,
        macroPath,
        ResolveMode.OTHER,
        withInvisibleItems = false  // because we expand only cfg-enabled macros
    )
    val defItem = perNs.resolvedDef.macros ?: return null
    return getMacroInfo(defItem)
}

private fun CrateDefMap.resolveNameInExternPrelude(name: String): PerNs {
    val defMap = externPrelude[name] ?: return PerNs.Empty
    return defMap.rootAsPerNs
}

// only when resolving `name` in `extern crate name;`
//                                             ~~~~
fun CrateDefMap.resolveExternCrateAsDefMap(name: String): CrateDefMap? =
    if (name == "self") this else directDependenciesDefMaps[name]

/**
 * Resolve in:
 * - legacy scope of macro (needed e.g. for `use name_ as name;`)
 * - current module / scope
 * - extern prelude
 * - std prelude
 */
private fun CrateDefMap.resolveNameInModule(modData: ModData, name: String): PerNs {
    val fromLegacyMacro = modData.legacyMacros[name]
        ?.let {
            val visibility = if (it.hasMacroExport) Visibility.Public else modData.visibilityInSelf
            val visItem = VisItem(modData.path.append(name), visibility)
            PerNs(macros = visItem)
        } ?: PerNs.Empty
    val fromScope = modData.getVisibleItem(name)
    val fromExternPrelude = resolveNameInExternPrelude(name)
    val fromPrelude = resolveNameInPrelude(name)
    return fromLegacyMacro.or(fromScope).or(fromExternPrelude).or(fromPrelude)
}

private fun CrateDefMap.resolveNameInCrateRootOrExternPrelude(name: String): PerNs {
    val fromCrateRoot = root.getVisibleItem(name)
    val fromExternPrelude = resolveNameInExternPrelude(name)

    return fromCrateRoot.or(fromExternPrelude)
}

private fun CrateDefMap.resolveNameInPrelude(name: String): PerNs {
    val prelude = prelude ?: return PerNs.Empty
    return prelude.getVisibleItem(name)
}

private sealed class PathKind {
    object Plain : PathKind()

    /** `self` is `Super(0)` */
    class Super(val level: Int) : PathKind()

    /** Starts with crate */
    object Crate : PathKind()

    /** Starts with :: */
    object Absolute : PathKind()

    /** `$crate` from macro expansion */
    class DollarCrate(val crateId: CratePersistentId) : PathKind()
}

private data class PathInfo(
    val kind: PathKind,
    /** number of segments represented by [kind]. */
    val segmentsToSkip: Int,
)

/**
 * Examples:
 * - For path               'foo::bar' returns `PathKindInfo([PathKind.Plain], 0)`
 * - For path        'super::foo::bar' returns `PathKindInfo([PathKind.Super], 1)`
 * - For path 'super::super::foo::bar' returns `PathKindInfo([PathKind.Super], 2)`
 */
private fun getPathKind(path: Array<String>): PathInfo {
    return when (path.first()) {
        MACRO_DOLLAR_CRATE_IDENTIFIER -> {
            val crateId = path.getOrNull(1)?.toIntOrNull()
            if (crateId != null) {
                PathInfo(PathKind.DollarCrate(crateId), 2)
            } else {
                RESOLVE_LOG.warn("Invalid path starting with dollar crate: '${path.contentToString()}'")
                PathInfo(PathKind.Plain, 0)
            }
        }
        "crate" -> PathInfo(PathKind.Crate, 1)
        "super" -> {
            var level = 0
            while (path.getOrNull(level) == "super") ++level
            PathInfo(PathKind.Super(level), level)
        }
        "self" -> {
            if (path.getOrNull(1) == "super") {
                val kind = getPathKind(path.copyOfRange(1, path.size))
                kind.copy(segmentsToSkip = kind.segmentsToSkip + 1)
            } else {
                PathInfo(PathKind.Super(0), 1)
            }
        }
        "" -> PathInfo(PathKind.Absolute, 1)
        else -> PathInfo(PathKind.Plain, 0)
    }
}

data class ResolvePathResult(
    val resolvedDef: PerNs,
    val reachedFixedPoint: Boolean,
    val visitedOtherCrate: Boolean,
) {
    companion object {
        fun empty(reachedFixedPoint: Boolean): ResolvePathResult =
            ResolvePathResult(PerNs.Empty, reachedFixedPoint, false)
    }
}
