/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyInfer
import org.rust.lang.core.types.ty.TyTypeParameter

sealed class Predicate: TypeFoldable<Predicate> {
    /** where T : Bar<A,B,C> */
    data class Trait(val trait: TraitRef) : Predicate() {
        override fun superFoldWith(folder: TypeFolder): Trait =
            Trait(trait.foldWith(folder))
    }

    /** where <T as TraitRef>::Name == X */
    data class Projection(
        val selfTy: Ty,
        val trait: RsTraitItem,
        val target: RsTypeAlias,
        val ty: Ty
    ): Predicate() {
        override fun superFoldWith(folder: TypeFolder): Projection =
            Projection(selfTy.foldWith(folder), trait, target, ty.foldWith(folder))

        override fun toString(): String =
            "<$selfTy as ${trait.name}>::${target.name} == $ty"
    }

    /** where `T1 == T2` */
    data class Equate(val ty1: Ty, val ty2: Ty) : Predicate() {
        override fun superFoldWith(folder: TypeFolder): Predicate =
            Equate(ty1.foldWith(folder), ty2.foldWith(folder))

        override fun toString(): String =
            "$ty1 == $ty2"
    }
}

data class Obligation(val recursionDepth: Int, var predicate: Predicate): TypeFoldable<Obligation> {
    constructor(predicate: Predicate) : this(0, predicate)

    override fun superFoldWith(folder: TypeFolder): Obligation =
        copy(predicate = predicate.foldWith(folder))
}

data class PendingPredicateObligation(val obligation: Obligation, val stalledOn: MutableList<Ty>)

class ObligationForest {
    enum class NodeState {
        /** Obligations for which selection had not yet returned a non-ambiguous result */
        Pending,

        /** This obligation was selected successfully, but may or may not have subobligations */
        Success,

        /** This obligation was resolved to an error. Error nodes are removed from the vector by the compression step */
        Error,
    }

    class Node(val obligation: PendingPredicateObligation) {
        var state: NodeState = NodeState.Pending
    }

    private val nodes: MutableList<Node> = mutableListOf()

    val pendingObligations: Sequence<PendingPredicateObligation> =
        nodes.asSequence().filter { it.state == NodeState.Pending }.map { it.obligation }

    @Suppress("UNUSED_PARAMETER") // TODO use `parent`
    fun registerObligationAt(obligation: PendingPredicateObligation, parent: Node?) {
        nodes.add(Node(obligation))
    }

    fun processObligations(processor: (PendingPredicateObligation) -> ProcessPredicateResult): Boolean {
        var stalled = true
        for (index in 0 until nodes.size) {
            val node = nodes[index]
            if (node.state != NodeState.Pending) continue

            val result = processor(node.obligation)
            when (result) {
                is ProcessPredicateResult.NoChanges -> {}
                is ProcessPredicateResult.Ok -> {
                    stalled = false
                    node.state = NodeState.Success
                    for (child in result.children) {
                        registerObligationAt(child, node)
                    }
                }
                is ProcessPredicateResult.Err -> {
                    stalled = false
                    node.state = NodeState.Error
                }
            }
        }

        if (!stalled) {
            nodes.removeIf { it.state != NodeState.Pending }
        }

        return stalled
    }
}

class FulfillmentContext(val ctx: RsInferenceContext, val lookup: ImplLookup) {

    private val obligations: ObligationForest = ObligationForest()

    val pendingObligations: Sequence<PendingPredicateObligation> =
        obligations.pendingObligations

    fun registerPredicateObligation(obligation: Obligation) {
        obligations.registerObligationAt(
            PendingPredicateObligation(ctx.resolveTypeVarsIfPossible(obligation), mutableListOf()),
            null
        )
    }

    fun selectWherePossible() {
        while (!obligations.processObligations(this::processPredicate)) {}
    }

    private fun processPredicate(pendingObligation: PendingPredicateObligation): ProcessPredicateResult {
        val (obligation, stalledOn) = pendingObligation
        if (!stalledOn.isEmpty()) {
            val nothingChanged = stalledOn.all {
                val resolvedTy = ctx.shallowResolve(it)
                resolvedTy == it
            }
            if (nothingChanged) return ProcessPredicateResult.NoChanges
            stalledOn.clear()
        }

        obligation.predicate = ctx.resolveTypeVarsIfPossible(obligation.predicate)
        val predicate = obligation.predicate

        when (predicate) {
            is Predicate.Trait -> {
                val selfTy = predicate.trait.selfTy
                if (selfTy is TyInfer) return ProcessPredicateResult.NoChanges
                val (trait, subst) = predicate.trait.trait
                val impl = lookup.findImplOfTrait(selfTy, trait) ?: return ProcessPredicateResult.Err
                return ProcessPredicateResult.Ok(subst.mapNotNull { (k, ty1) ->
                    impl.subst[k]?.let { ty2 ->
                        PendingPredicateObligation(Obligation(
                            obligation.recursionDepth + 1,
                            Predicate.Equate(ty1, ty2)
                        ), mutableListOf())
                    }
                })
            }
            is Predicate.Equate -> {
                ctx.combineTypes(predicate.ty1, predicate.ty2)
                return ProcessPredicateResult.Ok()
            }
            is Predicate.Projection -> {
                val selfTy = predicate.selfTy
                if (selfTy is TyInfer) return ProcessPredicateResult.NoChanges
                val impl = lookup.findImplOfTrait(selfTy, predicate.trait) ?: return ProcessPredicateResult.Err
                val theTy = impl.subst[TyTypeParameter.associated(predicate.target)] ?: return ProcessPredicateResult.Err
                return ProcessPredicateResult.Ok(PendingPredicateObligation(Obligation(
                    obligation.recursionDepth + 1,
                    Predicate.Equate(theTy, predicate.ty)
                ), mutableListOf()))
            }
        }
    }
}

sealed class ProcessPredicateResult {
    object NoChanges: ProcessPredicateResult()
    data class Ok(val children: List<PendingPredicateObligation>): ProcessPredicateResult() {
        constructor(vararg children: PendingPredicateObligation): this(listOf(*children))
    }
    object Err: ProcessPredicateResult()
}
