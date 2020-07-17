/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.analysis.mutability

import org.rust.ide.refactoring.findBinding
import org.rust.lang.core.DataFlowContext
import org.rust.lang.core.DataFlowOperator
import org.rust.lang.core.FlowDirection
import org.rust.lang.core.KillFrom
import org.rust.lang.core.cfg.ControlFlowGraph
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.analysis.*
import org.rust.lang.core.types.controlFlowGraph
import org.rust.lang.core.types.infer.RsInferenceResult
import org.rust.lang.core.types.inference

data class UnusedMutability(val mutableDeclarations: List<ProblematicDeclaration>)

class UnusedMutabilityContext private constructor(
    val inference: RsInferenceResult,
    val body: RsBlock,
    val cfg: ControlFlowGraph,
    val implLookup: ImplLookup = ImplLookup.relativeTo(body),
    private val mutableDeclarations: MutableList<ProblematicDeclaration> = mutableListOf()
) {
    fun reportNeedlesslyMutableDeclaration(binding: RsPatBinding, kind: DeclarationKind) {
        mutableDeclarations.add(ProblematicDeclaration(binding, kind))
    }

    fun check(): UnusedMutability? {
        val gatherMutabilityContext = GatherUsageContext(MutabilityData())
        val mutabilityData = gatherMutabilityContext.gather(body, implLookup, inference)
        val flowedMutability = FlowedMutabilityData.buildFor(this, mutabilityData, cfg)
        flowedMutability.check()
        return UnusedMutability(mutableDeclarations)
    }

    companion object {
        fun buildFor(owner: RsInferenceContextOwner): UnusedMutabilityContext? {
            val body = owner.body as? RsBlock ?: return null
            val cfg = owner.controlFlowGraph ?: return null
            return UnusedMutabilityContext(owner.inference, body, cfg)
        }
    }
}

object MutabilityDataFlowOperator : DataFlowOperator {
    override fun join(succ: Int, pred: Int): Int = succ or pred     // liveness from both preds are in scope
    override val initialValue: Boolean get() = false                // does not need mutability by default
}

private typealias MutabilityDataFlow = DataFlowContext<MutabilityDataFlowOperator>

private class FlowedMutabilityData(
    private val ctx: UnusedMutabilityContext,
    private val mutabilityData: UsageAnalysisData<MutabilityDataFlow>,
    private val dfcxLivePaths: MutabilityDataFlow
) {
    private fun isImmutable(usagePath: UsagePath): Boolean {
        var isImmutable = true
        dfcxLivePaths.eachBitOnEntry(usagePath.declaration) { index ->
            val path = mutabilityData.paths[index]
            // mutable usage of `a`
            if (usagePath == path) {
                isImmutable = false
            } else {
                // declaration/assignment of `a`, use of `a.b.c`
                val isEachExtensionDead = mutabilityData.eachBasePath(path) { it != usagePath }
                if (!isEachExtensionDead) isImmutable = false
            }
            isImmutable
        }
        return isImmutable
    }

    fun check() {
        for (path in mutabilityData.paths) {
            if (path is UsagePath.Base && path.declaration.isMutable() && isImmutable(path)) {
                ctx.reportNeedlesslyMutableDeclaration(path.declaration, path.declarationKind)
            }
        }
    }

    companion object {
        fun buildFor(ctx: UnusedMutabilityContext, mutabilityData: UsageAnalysisData<MutabilityDataFlow>,
                     cfg: ControlFlowGraph): FlowedMutabilityData {
            val dfcxLivePaths = DataFlowContext(cfg, MutabilityDataFlowOperator, mutabilityData.paths.size, FlowDirection.Backward)

            mutabilityData.addGenKills(dfcxLivePaths)
            dfcxLivePaths.propagate()

            return FlowedMutabilityData(ctx, mutabilityData, dfcxLivePaths)
        }
    }
}

private fun isMutableContext(element: RsElement): Boolean {
    val parent = element.parent
    val binOp = parent as? RsBinaryExpr
    // element = ...
    if (binOp?.isAssignBinaryExpr == true && binOp.left == element) {
        return true
    }
    // &mut <element>
    if ((parent as? RsUnaryExpr)?.operatorType == UnaryOperator.REF_MUT) {
        return true
    }
    // lambda calls
    if (parent is RsCallExpr) {
        return true
    }

    // mutable pattern matching
    if (parent is RsCondition) {
        val mutable = parent.patList?.any { it.isMutable() } ?: false
        if (mutable) return true
    }
    if (parent is RsMatchExpr) {
        val mutable = parent.matchBody?.matchArmList?.any { it.patList.any { pat -> pat.isMutable() } } ?: false
        if (mutable) return true
    }

    // let ref mut b = <element>;
    if (parent is RsLetDecl && parent.expr == element) {
        val binding = parent.findBinding()
        if (binding?.kind is RsBindingModeKind.BindByReference && binding.topLevelPattern.isMutable()) {
            return true
        }
    }

    // &mut self method calls
    if (element is RsDotExpr) {
        if (element.methodCall != null) {
            val function = element.methodCall?.reference?.resolve() as? RsFunction
            if (function != null && function.isMethod && function.selfParameter?.mutability?.isMut == true) {
                return true
            }
        }
    }
    if (parent is RsDotExpr && isMutableContext(parent)) {
        return true
    }

    return false
}

private fun RsPat?.isMutable(): Boolean {
    if (this == null) return false
    return when (this) {
        is RsPatRef -> mut != null
        is RsPatTupleStruct -> patList.any { it.isMutable() }
        is RsPatTup -> patList.any { it.isMutable() }
        is RsPatField -> patFieldFull?.pat.isMutable() || patBinding.isMutable()
        is RsPatIdent -> pat.isMutable() || patBinding.isMutable()
        else -> false
    }
}

private fun RsPatBinding?.isMutable(): Boolean {
    if (this == null) return false
    val mutability = when (val bindingKind = kind) {
        is RsBindingModeKind.BindByReference -> bindingKind.mutability
        is RsBindingModeKind.BindByValue -> bindingKind.mutability
    }
    return mutability.isMut
}

class MutabilityData : UsageAnalysisData<MutabilityDataFlow>() {
    override fun addGenKills(dataflow: MutabilityDataFlow) {
        for (usage in usages) {
            val bit = pathToIndex[usage.path] ?: error("No such usage path in pathToIndex")
            if (isMutableContext(usage.element)) {
                dataflow.addGen(usage.element, bit)
            }
        }
        for (declaration in declarations) {
            val bit = pathToIndex[declaration.path] ?: error("No such declaration path in pathToIndex")
            dataflow.addKill(KillFrom.ScopeEnd, declaration.element, bit)
        }
    }
}
