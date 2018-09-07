/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.types.infer.Obligation
import org.rust.lang.core.types.infer.Predicate
import org.rust.lang.core.types.infer.RsInferenceContext
import org.rust.lang.core.types.ty.*

/**
 * Returns the set of obligations needed to make [ty] well-formed.
 * If [ty] contains unresolved inference variables, this may include further WF obligations. However, if [ty] IS an
 * unresolved inference variable, returns `null`, because we are not able to make any progress at all. This is to
 * prevent "livelock" where we say "$0 is WF if $0 is WF".
 */
fun RsInferenceContext.getObligations(ty: Ty, body: RsElement, callerBounds: List<Predicate> = emptyList()): List<Obligation>? {
    val wf = WfPredicates(this, body, callerBounds)
    return if (wf.compute(ty)) wf.normalize() else null
}

data class WfPredicates(val ctx: RsInferenceContext, val body: RsElement, val callerBounds: List<Predicate>) {
    val out: MutableList<Obligation> = mutableListOf()

    fun compute(ty: Ty): Boolean {
        val walk = ty.walk()
        for (subTy in walk) {
            when (subTy) {
                is TyProjection -> {
                    walk.skipCurrentSubtree()  // subtree handled by `computeProjection`
                    computeProjection(subTy)
                }
                is TyAdt -> {
                    // WfNominalType
                    val obligations = getNominalObligations(subTy.item, subTy.typeParameterValues)
                    out.addAll(obligations)
                }
                is TyReference -> {
                    // WfReference
                    // TODO: add cause
                    out.add(Obligation(Predicate.TypeOutlives(subTy.region, subTy.referenced, body)))
                }
                is TyInfer -> {
                    val resolved = ctx.shallowResolve(ty)
                    if (resolved is TyInfer) {  // not yet resolved...
                        if (ty == resolved) return false  // ...this is the type we started from! no progress.
                        out.add(Obligation(Predicate.WellFormed(resolved, body)))
                    } else {
                        // Yes, resolved, proceed with the result.
                        // Should never return false because [ty] is not a [TyInfer].
                        compute(resolved)
                    }
                }
                // TODO: handle TyTraitObject
            }
        }
        return true
    }

    /** Pushes the obligations required for `trait_ref::Item` to be WF into [out]. */
    fun computeProjection(ty: TyProjection) {
        // A projection is well-formed if
        //   (a) the trait ref itself is WF and
        //   (b) the trait-ref holds.
        // (It may also be normalizable and be WF that way.)
        // TODO
    }

    fun getNominalObligations(item: RsStructOrEnumItemElement, subst: Substitution): List<Obligation> {
        // TODO
        return emptyList()
    }

    fun normalize(): List<Obligation> = out
}
