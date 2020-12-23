/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.ext.hasMacroExport

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
            val withLegacyMacros = mode == ResolveMode.IMPORT && path.size == 1
            resolveNameInModule(containingMod, firstSegment, withLegacyMacros)
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

fun CrateDefMap.resolveMacroCallToMacroDefInfo(
    containingMod: ModData,
    macroPath: Array<String>,
    macroIndex: MacroIndex
): MacroDefInfo? {
    if (macroPath.size == 1) {
        val name = macroPath.single()
        containingMod.legacyMacros[name]
            ?.getLastBefore(macroIndex)
            ?.let { return it }
    }

    val perNs = resolvePathFp(
        containingMod,
        macroPath,
        ResolveMode.OTHER,
        withInvisibleItems = false  // because we expand only cfg-enabled macros
    )
    val defItem = perNs.resolvedDef.macros ?: return null
    return getMacroInfo(defItem)
}

fun List<MacroDefInfo>.getLastBefore(macroIndex: MacroIndex): MacroDefInfo? =
    filter { it.macroIndex < macroIndex }.maxBy { it.macroIndex }

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
private fun CrateDefMap.resolveNameInModule(modData: ModData, name: String, withLegacyMacros: Boolean): PerNs {
    val fromLegacyMacro = if (withLegacyMacros) modData.getFirstLegacyMacro(name) ?: PerNs.Empty else PerNs.Empty
    val fromScope = modData.getVisibleItem(name)
    val fromExternPrelude = resolveNameInExternPrelude(name)
    val fromPrelude = resolveNameInPrelude(name)
    return fromLegacyMacro.or(fromScope).or(fromExternPrelude).or(fromPrelude)
}

/**
 * We take first macro, because this code is used for resolution inside import:
 * ```
 * macro_rules! name_ { ... }
 * use name_ as name;
 * ```
 *
 * Multiple macro definitions before import will cause compiler error:
 * ```
 * macro_rules! name_ { ... }
 * macro_rules! name_ { ... }
 * use name_ as name;
 * // error[E0659]: `name_` is ambiguous
 * ```
 */
private fun ModData.getFirstLegacyMacro(name: String): PerNs? {
    val def = legacyMacros[name]?.firstOrNull() ?: return null
    val visibility = if (def.hasMacroExport) Visibility.Public else visibilityInSelf
    val visItem = VisItem(path.append(name), visibility)
    return PerNs(macros = visItem)
}

/**
 * Used when macro path is qualified.
 * - It is either macro from other crate,
 *   then it will have `macro_export` attribute
 *   (and there can't be two `macro_export` macros with same name in same mod).
 * - Or it is reexport of legacy macro, then we peek first (see [getFirstLegacyMacro] for details)
 */
fun List<MacroDefInfo>.singlePublicOrFirst(): MacroDefInfo = singleOrNull { it.hasMacroExport } ?: first()
fun List<RsMacro>.singlePublicOrFirst(): RsMacro = singleOrNull { it.hasMacroExport } ?: first()

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
