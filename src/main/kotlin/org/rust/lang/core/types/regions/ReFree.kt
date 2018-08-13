/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import org.rust.lang.core.psi.ext.RsInferenceContextOwner
import org.rust.lang.core.types.infer.outlives.FreeRegionMap

/**
 * A "free" region can be interpreted as "some region at least as big as the scope of [contextOwner]".
 * When checking a function body, the types of all arguments and so forth that refer to bound region parameters are
 * modified to refer to free region parameters.
 */
data class ReFree(val contextOwner: RsInferenceContextOwner, val bound: BoundRegion) : Region()

/**
 * Combines a [ScopeTree] (which governs relationships between scopes) and a [FreeRegionMap] (which governs
 * relationships between free regions) to yield a complete relation between concrete regions.
 */
data class RegionRelations(
    /** Context used to fetch the region maps. */
    val contextOwner: RsInferenceContextOwner,

    /** Region maps for the given context. */
    val regionScopeTree: ScopeTree,

    /** Free-region relationships. */
    val freeRegions: FreeRegionMap
) {

    /** Determines whether one region is a subregion of another. */
    fun isSubRegionOf(sub: Region, sup: Region): Boolean {
        if (sub == sup) return true
        val result = when {
            sub === ReEmpty && sup === ReStatic -> true
            sub is ReScope && sup is ReScope ->
                regionScopeTree.isSubScopeOf(sub.scope, sup.scope)
            sub is ReScope && sup is ReEarlyBound -> {
                val freeScope = regionScopeTree.getEarlyFreeScope(sup)
                regionScopeTree.isSubScopeOf(sub.scope, freeScope)
            }
            sub is ReScope && sup is ReFree -> {
                val freeScope = regionScopeTree.getFreeScope(sup)
                regionScopeTree.isSubScopeOf(sub.scope, freeScope)
            }
            (sub is ReEarlyBound || sub is ReFree) && (sup is ReEarlyBound || sup is ReFree) ->
                freeRegions.isFreeSubRegionOf(sub, sup)
            else -> false
        }
        return result || isStatic(sup)
    }

    fun getLeastUpperBoundOfFreeRegions(region1: Region, region2: Region) =
        freeRegions.getLeastUpperBoundOfFreeRegions(region1, region2)

    /** Determines whether this free-region is required to be 'static. */
    private fun isStatic(sup: Region): Boolean =
        when (sup) {
            is ReStatic -> true
            is ReEarlyBound, is ReFree -> freeRegions.isFreeSubRegionOf(ReStatic, sup)
            else -> false
        }
}
