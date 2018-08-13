/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer.outlives

import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReFree
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.Region

class FreeRegionMap {
    /**
     * Stores the relation `a < b`, where `a` and `b` are regions.
     * Invariant: only free regions like `'x` or `'static` are stored in this relation, not scopes.
     */
    private val relation: TransitiveRelation<Region> = TransitiveRelation()

    fun isEmpty(): Boolean = relation.isEmpty()

    /**
     * Record that `'[sup]: '[sub]`. Or, put another way, `'[sub] <= '[sup]`.
     * (with the exception that `'static: 'x` is not notable)
     */
    fun relateRegions(sub: Region, sup: Region) {
        if (isFreeOrStatic(sub) && isFree(sup)) {
            relation.add(sub, sup)
        }
    }

    /**
     * Compute the least-upper-bound of two free regions.
     * In some cases, this is more conservative than necessary, in order to avoid making arbitrary choices.
     */
    fun getLeastUpperBoundOfFreeRegions(region1: Region, region2: Region): Region {
        require(isFree(region1) && isFree(region2))
        return if (region1 == region2) {
            region1
        } else {
            relation.getPostdomUpperBound(region1, region2) ?: ReStatic
        }
    }

    /** Tests whether `[sub] <= [sup]`. Both must be free regions or `'static`. */
    fun isFreeSubRegionOf(sub: Region, sup: Region): Boolean {
        require(isFreeOrStatic(sub) && isFreeOrStatic(sup))
        return sup == ReStatic || sub == sup || relation.contains(sub, sup)
    }
}

private fun isFree(region: Region): Boolean = region is ReEarlyBound || region is ReFree

private fun isFreeOrStatic(region: Region): Boolean = isFree(region) || region === ReStatic
