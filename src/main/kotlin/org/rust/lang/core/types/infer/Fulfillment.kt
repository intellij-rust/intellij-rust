/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.SelectionResult
import org.rust.lang.core.resolve.withSubst
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyInfer

sealed class Predicate: TypeFoldable<Predicate> {
    /** where T : Bar<A,B,C> */
    data class Trait(val trait: TraitRef) : Predicate() {
        override fun superFoldWith(folder: TypeFolder): Trait =
            Trait(trait.foldWith(folder))

        override fun superVisitWith(visitor: TypeVisitor): Boolean =
            trait.visitWith(visitor)
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

        override fun superVisitWith(visitor: TypeVisitor): Boolean =
            selfTy.visitWith(visitor) || ty.visitWith(visitor)

        override fun toString(): String =
            "<$selfTy as ${trait.name}>::${target.name} == $ty"
    }

    /** where `T1 == T2` */
    data class Equate(val ty1: Ty, val ty2: Ty) : Predicate() {
        override fun superFoldWith(folder: TypeFolder): Predicate =
            Equate(ty1.foldWith(folder), ty2.foldWith(folder))

        override fun superVisitWith(visitor: TypeVisitor): Boolean =
            ty1.visitWith(visitor) || ty2.visitWith(visitor)

        override fun toString(): String =
            "$ty1 == $ty2"
    }
}

data class Obligation(val recursionDepth: Int, var predicate: Predicate): TypeFoldable<Obligation> {
    constructor(predicate: Predicate) : this(0, predicate)

    override fun superFoldWith(folder: TypeFolder): Obligation =
        copy(predicate = predicate.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        predicate.visitWith(visitor)
}

data class PendingPredicateObligation(val obligation: Obligation, val stalledOn: MutableList<Ty>)

/**
 * [ObligationForest] is a mutable collection of obligations.
 * It's caller's responsibility to add new obligations via
 * [registerObligationAt] and to remove satisfied obligations
 * as a side effect of [processObligations].
 */
class ObligationForest {
    enum class NodeState {
        /** Obligations for which selection had not yet returned a non-ambiguous result */
        Pending,

        /** This obligation was selected successfully, but may or may not have subobligations */
        Success,

        /** This obligation was resolved to an error. Error nodes are removed from the vector by the compression step */
        Error,
    }

    data class ProcessObligationsResult(
        val hasErrors: Boolean,
        val stalled: Boolean
    )

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

    fun processObligations(
        processor: (PendingPredicateObligation) -> ProcessPredicateResult,
        breakOnFirstError: Boolean = false
    ): ProcessObligationsResult {
        var hasErrors = false
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
                    hasErrors = true
                    stalled = false
                    node.state = NodeState.Error
                    if (breakOnFirstError) return ProcessObligationsResult(hasErrors, stalled)
                }
            }
        }

        if (!stalled) {
            nodes.removeIf { it.state != NodeState.Pending }
        }

        return ProcessObligationsResult(hasErrors, stalled)
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
        while (!obligations.processObligations(this::processPredicate).stalled) {}
    }

    fun selectUntilError(): Boolean {
        do {
            val res = obligations.processObligations(this::processPredicate, breakOnFirstError = true)
            if (res.hasErrors) return false
        } while (!res.stalled)

        return true
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
                if (predicate.trait.selfTy is TyInfer) return ProcessPredicateResult.NoChanges
                val impl = lookup.select(predicate.trait, obligation.recursionDepth)
                return when (impl) {
                    is SelectionResult.Err -> ProcessPredicateResult.Err
                    is SelectionResult.Ambiguous -> ProcessPredicateResult.NoChanges
                    is SelectionResult.Ok -> {
                        return ProcessPredicateResult.Ok(impl.result.nestedObligations.map {
                            PendingPredicateObligation(it, mutableListOf())
                        })
                    }
                }
            }
            is Predicate.Equate -> {
                ctx.combineTypes(predicate.ty1, predicate.ty2)
                return ProcessPredicateResult.Ok()
            }
            is Predicate.Projection -> {
                val selfTy = predicate.selfTy
                if (selfTy is TyInfer) return ProcessPredicateResult.NoChanges
                val result = lookup.selectProjection(
                    TraitRef(selfTy, predicate.trait.withSubst()), // TODO use subst
                    predicate.target,
                    obligation.recursionDepth
                )
                return when (result) {
                    is SelectionResult.Err -> ProcessPredicateResult.Err
                    is SelectionResult.Ambiguous -> ProcessPredicateResult.NoChanges
                    is SelectionResult.Ok -> {
                        val theTy = result.result ?: return ProcessPredicateResult.Err
                        ProcessPredicateResult.Ok((theTy.obligations + Obligation(
                            obligation.recursionDepth + 1,
                            Predicate.Equate(theTy.value, predicate.ty)
                        )).map {PendingPredicateObligation(it, mutableListOf()) })
                    }
                }
            }
        }
    }

    fun <T> TyWithObligations<T>.register(): T {
        obligations.forEach { registerPredicateObligation(it) }
        return value
    }
}

sealed class ProcessPredicateResult {
    object Err: ProcessPredicateResult()
    object NoChanges: ProcessPredicateResult()
    data class Ok(val children: List<PendingPredicateObligation>): ProcessPredicateResult() {
        constructor(vararg children: PendingPredicateObligation): this(listOf(*children))
    }
}
