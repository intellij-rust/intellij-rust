/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.types.ty.*

class Autoderef(
    private val lookup: ImplLookup,
    private val ctx: RsInferenceContext,
    baseTy: Ty,
) : Sequence<Ty> {
    private val visitedTys: MutableSet<Ty> = hashSetOf()
    private val steps: MutableList<AutoderefStep> = mutableListOf()

    private val obligations: MutableList<Obligation> = mutableListOf()

    private val sequence = generateSequence(ctx.resolveTypeVarsIfPossible(baseTy)) { from ->
        if (visitedTys.add(from)) {
            val deref = lookup.deref(from)
            if (deref != null && deref.obligations.isNotEmpty()) {
                val fulfillment = FulfillmentContext(ctx, lookup)
                fulfillment.registerPredicateObligations(deref.obligations)
                fulfillment.selectWherePossible()
                obligations += fulfillment.pendingObligations.map { it.obligation }
            }
            val to = deref?.value?.let(ctx::resolveTypeVarsWithObligations)
                ?: (from as? TyArray)?.let { array -> TySlice(array.base) }
            if (to != null) {
                steps += AutoderefStep(from, to)
            }
            to
        } else {
            null
        }
    }.constrainOnce().take(DEFAULT_RECURSION_LIMIT)

    override fun iterator(): Iterator<Ty> = sequence.iterator()

    fun steps(): List<AutoderefStep> = ArrayList(steps)

    fun obligations(): List<Obligation> = ArrayList(obligations)

    fun stepCount(): Int = steps.size

    data class AutoderefStep(
        val from: Ty,
        val to: Ty
    ) {
        fun getKind(items: KnownItems): AutoderefKind {
            val autoderefKind = when {
                from is TyReference || from is TyPointer || from is TyAdt && from.item == items.Box ->
                    AutoderefKind.Builtin
                from is TyArray && to is TySlice ->
                    AutoderefKind.ArrayToSlice
                else -> AutoderefKind.Overloaded
            }
            return autoderefKind
        }
    }

    enum class AutoderefKind {
        Builtin,
        Overloaded,
        ArrayToSlice
    }
}

fun List<Autoderef.AutoderefStep>.toAdjustments(items: KnownItems): List<Adjustment.Deref> = mapNotNull {
    when (it.getKind(items)) {
        Autoderef.AutoderefKind.Builtin -> Adjustment.Deref(it.to, null)
        Autoderef.AutoderefKind.Overloaded -> Adjustment.Deref(it.to, Mutability.IMMUTABLE)
        Autoderef.AutoderefKind.ArrayToSlice -> null
    }
}
