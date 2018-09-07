/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer.outlives

import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.getObligations
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyProjection
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.stdext.dequeOf

/**
 * Outlives bounds are relationships between generic parameters, whether they both be regions (`'a: 'b`) or whether
 * types are whether they both be regions (`'a: 'b`) or whether types are involved (`T: 'a`). These relationships can
 * be extracted from the full set of predicates we understand or also from types (in which case they are called implied
 * bounds). They are fed to the [OutlivesEnvironment] which in turn is supplied to the region checker and other parts
 * of the inference system.
 */
sealed class OutlivesBound {
    data class RegionSubRegion(val region1: Region, val region2: Region) : OutlivesBound()
    data class RegionSubParameter(val region: Region, val parameter: TyTypeParameter) : OutlivesBound()
    data class RegionSubProjection(val region: Region, val projection: TyProjection) : OutlivesBound()
}

/**
 * Implied bounds are region relationships that we deduce automatically.
 * The idea is that (e.g.) a caller must check that a function's argument types are well-formed immediately before
 * calling that fn, and hence the *callee* can assume that its argument types are well-formed.
 * This may imply certain relationships between generic parameters. For example:
 *
 * ```
 * fn foo<'a, T>(x: &'a T)
 * ```
 *
 * can only be called with a `'a` and `T` such that `&'a T` is WF.
 * For `&'a T` to be WF, `T: 'a` must hold. So we can assume `T: 'a`.
 */
fun RsInferenceContext.getImpliedOutlivesBounds(
    ty: Ty,
    body: RsElement,
    callerBounds: List<Predicate>
): List<OutlivesBound> {
    // Sometimes when we ask what it takes for T: WF, we get back that U: WF is required; in that case, we push U
    // onto this stack and process it next. Currently (at least) these resulting predicates are always guaranteed
    // to be a subset of the original type, so we need not fear non-termination.
    val wellFormedTypes = dequeOf(ty)
    val impliedBounds = mutableListOf<OutlivesBound>()
    val fulfillContext = FulfillmentContext(this, lookup)
    while (wellFormedTypes.isNotEmpty()) {
        val wellFormedTy = wellFormedTypes.pop()

        // Compute the obligations for `ty` to be well-formed. If `ty` is an unresolved inference variable, just
        // substituted an empty set -- because the return type here is going to be things we *add* to the environment,
        // it's always ok for this set to be smaller than the ultimate set.
        val obligations: List<Obligation> = getObligations(wellFormedTy, body, callerBounds).orEmpty()

        obligations
            .filter { it.predicate.hasTyInfer }
            .forEach(fulfillContext::registerPredicateObligation)

        impliedBounds.addAll(obligations.flatMap { obligation ->
            val predicate = obligation.predicate
            when (predicate) {
                is Predicate.RegionOutlives ->
                    listOf(OutlivesBound.RegionSubRegion(predicate.sub, predicate.sup))
                is Predicate.TypeOutlives -> {
                    val resolved = resolveTypeVarsIfPossible(predicate.supTy)
                    val components = getOutlivesComponents(resolved)
                    getImpliedBoundsFromComponents(predicate.subRegion, components)
                }
                is Predicate.WellFormed -> {
                    wellFormedTypes.add(predicate.ty)
                    emptyList()
                }
                else -> emptyList()
            }
        })
    }

    // Ensure that those obligations that we had to solve get solved *here*.
    fulfillContext.selectWherePossible()

    return impliedBounds
}

/**
 * When we have an implied bound that `T: 'a`, we can further break this down to determine what relationships would
 * have to hold for `T: 'a` to hold. We get to assume that the caller has validated those relationships.
 */
fun getImpliedBoundsFromComponents(subRegion: Region, supComponents: List<Component>): List<OutlivesBound> =
    supComponents.mapNotNull { component ->
        when (component) {
            is Component.Region -> OutlivesBound.RegionSubRegion(subRegion, component.value)
            is Component.Parameter -> OutlivesBound.RegionSubParameter(subRegion, component.value)
            is Component.Projection -> OutlivesBound.RegionSubProjection(subRegion, component.value)
            else -> null
        }
    }

fun getExplicitOutlivesBounds(callerBounds: List<Predicate>): List<OutlivesBound> =
    callerBounds
        .filterIsInstance<Predicate.RegionOutlives>()
        .map { OutlivesBound.RegionSubRegion(it.sub, it.sup) }
