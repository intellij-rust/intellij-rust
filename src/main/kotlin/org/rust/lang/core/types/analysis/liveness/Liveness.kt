/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.analysis.liveness

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

data class Liveness(val deadDeclarations: List<ProblematicDeclaration>)

class LivenessContext private constructor(
    val inference: RsInferenceResult,
    val body: RsBlock,
    val cfg: ControlFlowGraph,
    val implLookup: ImplLookup = ImplLookup.relativeTo(body),
    private val deadDeclarations: MutableList<ProblematicDeclaration> = mutableListOf()
) {
    fun reportDeadDeclaration(binding: RsPatBinding, kind: DeclarationKind) {
        deadDeclarations.add(ProblematicDeclaration(binding, kind))
    }

    fun check(): Liveness? {
        val gatherLivenessContext = GatherUsageContext(LivenessData())
        val livenessData = gatherLivenessContext.gather(body, implLookup, inference)
        val flowedLiveness = FlowedLivenessData.buildFor(this, livenessData, cfg)
        flowedLiveness.check()
        return Liveness(deadDeclarations)
    }

    companion object {
        fun buildFor(owner: RsInferenceContextOwner): LivenessContext? {
            val body = owner.body as? RsBlock ?: return null
            val cfg = owner.controlFlowGraph ?: return null
            return LivenessContext(owner.inference, body, cfg)
        }
    }
}

object LiveDataFlowOperator : DataFlowOperator {
    override fun join(succ: Int, pred: Int): Int = succ or pred     // liveness from both preds are in scope
    override val initialValue: Boolean get() = false                // dead by default
}

typealias LivenessDataFlow = DataFlowContext<LiveDataFlowOperator>

class FlowedLivenessData(
    private val ctx: LivenessContext,
    private val livenessData: UsageAnalysisData<LivenessDataFlow>,
    private val dfcxLivePaths: LivenessDataFlow
) {
    private fun isDead(usagePath: UsagePath): Boolean {
        var isDead = true
        return dfcxLivePaths.eachBitOnEntry(usagePath.declaration) { index ->
            val path = livenessData.paths[index]
            // declaration/assignment of `a`, use of `a`
            if (usagePath == path) {
                isDead = false
            } else {
                // declaration/assignment of `a`, use of `a.b.c`
                val isEachExtensionDead = livenessData.eachBasePath(path) { it != usagePath }
                if (!isEachExtensionDead) isDead = false
            }
            isDead
        }
    }

    fun check() {
        for (path in livenessData.paths) {
            if (path is UsagePath.Base && isDead(path)) {
                ctx.reportDeadDeclaration(path.declaration, path.declarationKind)
            }
        }
    }

    companion object {
        fun buildFor(ctx: LivenessContext, livenessData: UsageAnalysisData<LivenessDataFlow>, cfg: ControlFlowGraph): FlowedLivenessData {
            val dfcxLivePaths = DataFlowContext(cfg, LiveDataFlowOperator, livenessData.paths.size, FlowDirection.Backward)

            livenessData.addGenKills(dfcxLivePaths)
            dfcxLivePaths.propagate()

            return FlowedLivenessData(ctx, livenessData, dfcxLivePaths)
        }
    }
}

class LivenessData: UsageAnalysisData<LivenessDataFlow>() {
    override fun addGenKills(dataflow: LivenessDataFlow) {
        for (usage in usages) {
            val bit = pathToIndex[usage.path] ?: error("No such usage path in pathToIndex")
            dataflow.addGen(usage.element, bit)
        }
        for (declaration in declarations) {
            val bit = pathToIndex[declaration.path] ?: error("No such declaration path in pathToIndex")
            dataflow.addKill(KillFrom.ScopeEnd, declaration.element, bit)
        }
    }
}
