/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer.outlives

import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.infer.SubRegionOrigin.RelateParamBound
import org.rust.lang.core.types.infer.VerifyBound.*
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*

data class RegionObligation(val subRegion: Region, val supType: Ty, val cause: RegionObligationCause)

sealed class RegionObligationCause {
    abstract val element: RsElement

    /** A type like `&'a T` is WF only if `T: 'a`. */
    data class ReferenceOutlivesReferent(override val element: RsElement, val ty: Ty) : RegionObligationCause()

    /** Not well classified or should be obvious from element. */
    data class MiscObligation(override val element: RsElement) : RegionObligationCause()
}

/**
 * Registers that the given region obligation must be resolved from within the scope of [body].
 * These regions are enqueued and later processed by region inference, when full type information is available.
 */
fun RsInferenceContext.registerRegionObligation(body: RsElement, obligation: RegionObligation) {
    regionObligations.add(Pair(body, obligation))
}

/**
 * Process the region obligations that must be proven (during region inference) for the given [body], given information
 * about the region bounds in scope and so forth. This function must be invoked for all relevant bodies before region
 * inference is done (or else an assert will fire).
 *
 * @param regionBoundPairs    the set of region bounds implied by the parameters and where-clauses. In particular, each
 *                            pair `('a, K)` in this list tells us that the bounds in scope indicate that `K: 'a`,
 *                            where `K` is either a generic parameter like `T` or a projection like `T::Item`.
 * @param implicitRegionBound if some, this is a region bound that is considered to hold for all type parameters.
 * @param callerBounds        is the list caller's bounds for the enclosing function.
 * @param body                is the body whose region obligations are being processed.
 * @return                    This function may have to perform normalizations, and hence it returns an `InferOk` with
 *                            subobligations that must be processed.
 */
fun RsInferenceContext.processRegisteredRegionObligations(
    regionBoundPairs: MutableList<Pair<Region, GenericKind>>,
    implicitRegionBound: Region?,
    callerBounds: List<Predicate>,
    body: RsElement
) {
    val outlives = TypeOutlives(this, regionBoundPairs, implicitRegionBound, callerBounds)
    // pull out the region obligations with the given [body] (leaving the rest)
    val bodyObligations = regionObligations.inner
        .drainFilter { (element, _) -> element == body }
        .map { it.second }
    for ((subRegion, supType, cause) in bodyObligations) {
        val resolvedSupType = resolveTypeVarsIfPossible(supType)
        val origin = SubRegionOrigin.fromObligationCause(cause) { RelateParamBound(cause.element, supType) }
        outlives.typeMustOutlive(origin, resolvedSupType, subRegion)
    }
}

/** Processes a single ad-hoc region obligation that was not registered in advance. */
fun RsInferenceContext.typeMustOutlive(
    regionBoundPairs: MutableList<Pair<Region, GenericKind>>,
    implicitRegionBound: Region?,
    callerBounds: List<Predicate>,
    origin: SubRegionOrigin,
    ty: Ty,
    region: Region
) {
    val outlives = TypeOutlives(this, regionBoundPairs, implicitRegionBound, callerBounds)
    val resolved = resolveTypeVarsIfPossible(ty)
    outlives.typeMustOutlive(origin, resolved, region)
}

/**
 * The [TypeOutlives] struct has the job of "lowering" a `T: 'a` obligation into a series of `'a: 'b` constraints and
 * "verifys", as described on the module comment.
 */
data class TypeOutlives(
    val ctx: RsInferenceContext,
    val regionBoundPairs: MutableList<Pair<Region, GenericKind>>,
    val implicitRegionBound: Region?,
    val callerBounds: List<Predicate>
) {

    /**
     * Adds constraints to inference such that `T: 'a` holds.
     *
     * @param origin the reason we need this constraint
     * @param ty     the type `T`
     * @param region the region `'a`
     */
    fun typeMustOutlive(origin: SubRegionOrigin, ty: Ty, region: Region) {
        componentsMustOutlive(origin, getOutlivesComponents(ty), region)
    }

    private fun componentsMustOutlive(origin: SubRegionOrigin, components: List<Component>, region: Region) {
        for (component in components) {
            when (component) {
                is Component.Region -> ctx.makeSubRegion(origin, region, component.value)
                is Component.Parameter -> parameterMustOutlive(origin, region, component.value)
                is Component.Projection -> projectionMustOutlive(origin, region, component.value)
                else -> Unit  // TODO: report error?
            }
        }
    }

    private fun parameterMustOutlive(origin: SubRegionOrigin, region: Region, parameter: TyTypeParameter) {
        val verifyBound = getParameterBound(parameter)
        val generic = GenericKind.Parameter(parameter)
        ctx.verifyGenericBound(origin, generic, region, verifyBound)
    }

    private fun projectionMustOutlive(origin: SubRegionOrigin, region: Region, projection: TyProjection) {
        // Compute the bounds we can derive from the environment or trait definition.
        // We know that the projection outlives all the regions in this list.
        val bounds = getProjectionDeclaredBounds(projection)

        // If we know that the projection outlives 'static, then we're done here.
        if (bounds.contains(ReStatic)) return

        // If there are inference variables, then, we can break down the outlives into more primitive components
        // without adding unnecessary edges.
        if (bounds.isEmpty() && projection.needsInfer) {
            for (componentTy in projection.typeParameterValues.types) {
                typeMustOutlive(origin, componentTy, region)
            }
            for (componentRegion in projection.typeParameterValues.regions) {
                ctx.makeSubRegion(origin, region, componentRegion)
            }
            return
        }

        // If we find that there is a unique declared bound `'b`, and this bound appears in the trait reference, then
        // the best action is to require that `'b:'r`, so do that.
        val uniqueBounds = bounds.distinct()
        if (uniqueBounds.size == 1) {
            val uniqueBound = uniqueBounds.first()
            if (projection.typeParameterValues.regions.any { it == uniqueBound }) {
                ctx.makeSubRegion(origin, region, uniqueBound)
                return
            }
        }

        // Fallback to verifying after the fact that there exists a declared bound, or that all the components
        // appearing in the projection outlive.
        val verifyBound = getProjectionBound(bounds, projection)
        val generic = GenericKind.Projection(projection)
        ctx.verifyGenericBound(origin, generic, region, verifyBound)
    }

    private fun getTypeBound(ty: Ty): VerifyBound =
        when (ty) {
            is TyTypeParameter -> getParameterBound(ty)
            is TyProjection -> getProjectionBound(getProjectionDeclaredBounds(ty), ty)
            else -> getRecursiveTypeBound(ty)
        }

    private fun getParameterBound(parameter: TyTypeParameter): VerifyBound {
        val bounds = getDeclaredGenericBoundsFromEnvironment(GenericKind.Parameter(parameter))
        // Add in the default bound of fn body that applies to all in scope type parameters:
        implicitRegionBound?.let { bounds.add(it) }
        return AnyRegion(bounds)
    }

    private fun getProjectionDeclaredBounds(projection: TyProjection): List<Region> {
        // First assemble bounds from where clauses and traits.
        val bounds = getDeclaredGenericBoundsFromEnvironment(GenericKind.Projection(projection))
        bounds.addAll(getDeclaredProjectionBoundsFromTrait(projection))
        return bounds
    }

    private fun getProjectionBound(declaredBounds: List<Region>, projection: TyProjection): VerifyBound =
        AnyRegion(declaredBounds) or getRecursiveTypeBound(projection)

    private fun getRecursiveTypeBound(ty: Ty): VerifyBound {
        val bounds = mutableListOf<VerifyBound>()

        for (subTy in ty.walkShallow()) {
            bounds.add(getTypeBound(subTy))
        }

        bounds.add(AllRegions(ty.regions.toList()))

        // remove bounds that must hold, since they are not interesting
        bounds.removeAll { it.mustHold }

        return if (bounds.size == 1) bounds.first() else AllBounds(bounds)
    }

    private fun getDeclaredGenericBoundsFromEnvironment(generic: GenericKind): MutableList<Region> {
        val ty = when (generic) {
            is GenericKind.Parameter -> generic.ty
            is GenericKind.Projection -> generic.ty
        }
        val parameterBounds = collectOutlivesFromPredicateList(ty, callerBounds)
        regionBoundPairs
            .filter { it.second == generic }
            .mapTo(parameterBounds) { it.first }
        return parameterBounds
    }

    /** Given a projection like `<T as Foo<'x>>::Bar`, returns any bounds declared in the trait definition. */
    private fun getDeclaredProjectionBoundsFromTrait(projection: TyProjection): List<Region> {
        return emptyList()  // TODO
    }

    /** Searches through a predicate list for a predicate `T: 'a`. */
    private fun collectOutlivesFromPredicateList(ty: Ty, predicates: List<Predicate>): MutableList<Region> =
        predicates
            .filterIsInstance<Predicate.TypeOutlives>()
            .filter { it.supTy == ty }
            .map { it.subRegion }
            .toMutableList()
}

private inline fun <T> MutableList<T>.drainFilter(predicate: (T) -> Boolean): List<T> {
    val removed = mutableListOf<T>()
    val iterator = this.listIterator()
    while (iterator.hasNext()) {
        val element = iterator.next()
        if (predicate(element)) {
            iterator.remove()
            removed.add(element)
        }
    }
    return removed
}
