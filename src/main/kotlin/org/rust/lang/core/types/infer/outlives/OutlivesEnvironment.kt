/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer.outlives

import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.infer.GenericKind
import org.rust.lang.core.types.infer.GenericKind.Parameter
import org.rust.lang.core.types.infer.GenericKind.Projection
import org.rust.lang.core.types.infer.Predicate
import org.rust.lang.core.types.infer.RsInferenceContext
import org.rust.lang.core.types.infer.outlives.OutlivesBound.*
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReFree
import org.rust.lang.core.types.regions.ReVar
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.TyFunction

/**
 * The [OutlivesEnvironment] collects information about what outlives what in a given type-checking setting.
 * For example, if we have a where-clause like `where T: 'a` in scope, then the [OutlivesEnvironment] would record that.
 * Similarly, it contains methods for processing and adding implied bounds into the outlives environment.
 */
class OutlivesEnvironment(private val callerBounds: List<Predicate>) {
    val freeRegionMap: FreeRegionMap = FreeRegionMap()
    val regionBoundPairs: MutableList<Pair<Region, GenericKind>> = mutableListOf()

    init {
        addOutlivesBounds(null, getExplicitOutlivesBounds(callerBounds))
    }

    /**
     * This method adds "implied bounds" into the outlives environment.
     * Implied bounds are outlives relationships that we can deduce on the basis that certain types must be well-formed
     * -- these are either the types that appear in the function signature or else the input types to an impl.
     * For example, if you have a function like
     *
     * ```
     * fn foo<'a, 'b, T>(x: &'a &'b [T]) {}
     * ```
     *
     * we can assume in the caller's body that `'b: 'a` and that `T: 'b` (and hence, transitively, that `T: 'a`).
     * This method would add those assumptions into the outlives environment.
     */
    fun addImpliedBounds(ctx: RsInferenceContext, fnTy: TyFunction, body: RsElement) {
        val tys = fnTy.paramTypes + fnTy.retType
        for (ty in tys) {
            val resolved = ctx.resolveTypeVarsIfPossible(ty)
            val impliedBounds = ctx.getImpliedOutlivesBounds(resolved, body, callerBounds)
            addOutlivesBounds(ctx, impliedBounds)
        }
    }

    /**
     * Processes outlives bounds that are known to hold, whether from implied or other sources.
     * The [ctx] parameter is optional; if the implied bounds may contain inference variables, it must be
     * supplied, in which case we will register "givens" on the inference context.
     */
    private fun addOutlivesBounds(ctx: RsInferenceContext?, outlivesBounds: List<OutlivesBound>) {
        // Record relationships such as `T: 'x` that don't go into the free region map but which we use here.
        for (bound in outlivesBounds) {
            when (bound) {
                is RegionSubRegion -> {
                    val (sub, sup) = bound
                    if ((sub is ReEarlyBound || sub is ReFree) && sup is ReVar) {
                        checkNotNull(ctx).addGiven(sub, sup)
                    } else {
                        // In principle, we could record (and take advantage of) every relationship here, but we are
                        // also free not to -- it simply means strictly less that we can successfully type check. Right
                        // now we only look for things  relationships between free regions. (It may also be that we
                        // should revise our inference system to be more general and to make use of *every* relationship
                        // that arises here, but presently we do not.)
                        freeRegionMap.relateRegions(sub, sup)
                    }
                }
                is RegionSubParameter ->
                    regionBoundPairs.add(Pair(bound.region, Parameter(bound.parameter)))
                is RegionSubProjection ->
                    regionBoundPairs.add(Pair(bound.region, Projection(bound.projection)))
            }
        }
    }
}
