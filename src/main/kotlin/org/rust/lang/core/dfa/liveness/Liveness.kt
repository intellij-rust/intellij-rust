/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa.liveness

import org.rust.lang.core.dfa.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.controlFlowGraph
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.infer.RsInferenceResult
import org.rust.lang.core.types.inference

enum class DeclarationKind { Parameter, Variable }

data class DeadDeclaration(val binding: RsPatBinding, val kind: DeclarationKind)

data class DeadAssignment(val binding: RsPatBinding, val element: RsElement)

data class Liveness(
    val deadDeclarations: List<DeadDeclaration>,
    val deadAssignments: List<DeadAssignment>
)

class LivenessContext private constructor(
    val inference: RsInferenceResult,
    val body: RsBlock,
    val cfg: ControlFlowGraph,
    val implLookup: ImplLookup = ImplLookup.relativeTo(body),
    private val deadDeclarations: MutableList<DeadDeclaration> = mutableListOf(),
    private val deadAssignments: MutableList<DeadAssignment> = mutableListOf(),
) {
    fun reportDeadDeclaration(binding: RsPatBinding, kind: DeclarationKind) {
        deadDeclarations.add(DeadDeclaration(binding, kind))
    }

    fun reportDeadAssignment(binding: RsPatBinding, element: RsElement) {
        deadAssignments.add(DeadAssignment(binding, element))
    }

    fun check(): Liveness {
        val gatherLivenessContext = GatherLivenessContext(this)
        val livenessData = gatherLivenessContext.gather()
        val flowedLiveness = FlowedLivenessData.buildFor(this, livenessData, cfg)
        flowedLiveness.check()
        return Liveness(deadDeclarations, deadAssignments)
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
    private val livenessData: LivenessData,
    private val dfcxLivePaths: LivenessDataFlow,
    private val dfcxLiveAssignments: LivenessDataFlow,
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

    private fun isDeadAssignment(assignment: Assignment): Boolean {
        val usagePath = assignment.path
        var isDead = true
        return dfcxLiveAssignments.eachBitOnEntry(assignment.element) { index ->
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
        for (assignment in livenessData.assignments) {
            if (isDeadAssignment(assignment)) {
                ctx.reportDeadAssignment(assignment.path.declaration, assignment.element)
            }
        }
    }

    companion object {
        fun buildFor(ctx: LivenessContext, livenessData: LivenessData, cfg: ControlFlowGraph): FlowedLivenessData {
            val dfcxLivePaths = DataFlowContext(cfg, LiveDataFlowOperator, livenessData.paths.size, FlowDirection.Backward)
            val dfcxLiveAssignments = DataFlowContext(cfg, LiveDataFlowOperator, livenessData.paths.size, FlowDirection.Backward)

            livenessData.addGenKills(dfcxLivePaths, dfcxLiveAssignments)
            dfcxLivePaths.propagate()
            dfcxLiveAssignments.propagate()

            return FlowedLivenessData(ctx, livenessData, dfcxLivePaths, dfcxLiveAssignments)
        }
    }
}

class GatherLivenessContext(
    private val ctx: LivenessContext,
    private val livenessData: LivenessData = LivenessData()
) : Delegate {

    override fun consume(element: RsElement, cmt: Cmt, mode: ConsumeMode) {}

    override fun matchedPat(pat: RsPat, cmt: Cmt, mode: MatchMode) {}

    override fun consumePat(pat: RsPat, cmt: Cmt, mode: ConsumeMode) {
        pat.descendantsOfType<RsPatBinding>().forEach { binding ->
            livenessData.addDeclaration(binding)
        }
    }

    override fun declarationWithoutInit(binding: RsPatBinding) {
        livenessData.addDeclaration(binding)
    }

    override fun mutate(assignmentElement: RsElement, assigneeCmt: Cmt, mode: MutateMode) {
        val category = assigneeCmt.category
        if (category is Categorization.Local) {
            val binding = category.declaration as? RsPatBinding ?: return
            when (assignmentElement) {
                is RsBinaryExpr -> livenessData.addAssignment(binding, assignmentElement, false)
                is RsPatIdent -> {
                    val letDecl = assignmentElement.ancestorStrict<RsLetDecl>() ?: return
                    val letDeclPat = letDecl.pat ?: return
                    if (letDeclPat.isAncestorOf(assignmentElement)) {
                        livenessData.addAssignment(binding, assignmentElement, true)
                    }
                }
            }
        }
    }

    override fun useElement(element: RsElement, cmt: Cmt) {
        val binaryExpr = element.ancestorStrict<RsBinaryExpr>()
        if (binaryExpr != null && binaryExpr.left == element && binaryExpr.binaryOp.operatorType is AssignmentOp) {
            return
        }
        livenessData.addUsage(element, cmt)
    }

    fun gather(): LivenessData {
        val gatherVisitor = ExprUseWalker(this, MemoryCategorizationContext(ctx.implLookup, ctx.inference))
        gatherVisitor.consumeBody(ctx.body)
        return livenessData
    }
}

sealed class UsagePath {
    abstract val declaration: RsPatBinding

    data class Base(override val declaration: RsPatBinding) : UsagePath() {
        override fun toString(): String = declaration.text
    }

    data class Extend(val parent: UsagePath) : UsagePath() {
        override val declaration: RsPatBinding = parent.declaration
        override fun toString(): String = "Extend($parent)"
    }

    private val base: Base
        get() = when (this) {
            is Base -> this
            is Extend -> parent.base
        }

    val declarationKind: DeclarationKind
        get() = if (base.declaration.ancestorOrSelf<RsValueParameter>() != null) {
            DeclarationKind.Parameter
        } else {
            DeclarationKind.Variable
        }

    companion object {
        fun computeFor(cmt: Cmt): UsagePath? {
            return when (val category = cmt.category) {
                is Categorization.Rvalue -> {
                    val declaration = (cmt.element as? RsExpr)?.declaration as? RsPatBinding ?: return null
                    Base(declaration)
                }

                is Categorization.Local -> {
                    val declaration = category.declaration as? RsPatBinding ?: return null
                    Base(declaration)
                }

                is Categorization.Deref -> {
                    val baseCmt = category.unwrapDerefs()
                    computeFor(baseCmt)
                }

                is Categorization.Interior -> {
                    val baseCmt = category.cmt
                    val parent = computeFor(baseCmt) ?: return null
                    Extend(parent)
                }

                else -> null
            }
        }
    }
}

data class Declaration(val path: UsagePath.Base, val element: RsElement = path.declaration) {
    override fun toString(): String = "Declaration($path)"
}

data class Usage(val path: UsagePath, val element: RsElement) {
    override fun toString(): String = "Usage($path)"
}

data class Assignment(val path: UsagePath.Base, val element: RsElement, val isInit: Boolean) {
    override fun toString(): String = "Assignment($path)"
}

class LivenessData(
    val paths: MutableList<UsagePath> = mutableListOf(),
    val declarations: MutableSet<Declaration> = linkedSetOf(),
    val usages: MutableSet<Usage> = linkedSetOf(),
    val assignments: MutableSet<Assignment> = linkedSetOf(),
    private val pathToIndex: MutableMap<UsagePath, Int> = mutableMapOf()
) {
    private fun addUsagePath(usagePath: UsagePath) {
        if (!pathToIndex.containsKey(usagePath)) {
            val index = paths.size
            paths.add(usagePath)
            pathToIndex[usagePath] = index
        }
    }

    fun eachBasePath(usagePath: UsagePath, predicate: (UsagePath) -> Boolean): Boolean {
        var path = usagePath
        while (true) {
            if (!predicate(path)) return false
            when (path) {
                is UsagePath.Base -> return true
                is UsagePath.Extend -> path = path.parent
            }
        }
    }

    fun addGenKills(dfcxLivePaths: LivenessDataFlow, dfcxLiveAssignments: LivenessDataFlow) {
        for (usage in usages) {
            val bit = pathToIndex[usage.path] ?: error("No such usage path in pathToIndex")
            dfcxLivePaths.addGen(usage.element, bit)
            dfcxLiveAssignments.addGen(usage.element, bit)
        }
        for (assignment in assignments) {
            val bit = pathToIndex[assignment.path] ?: error("No such usage path in pathToIndex")
            if (!assignment.isInit) {
                dfcxLivePaths.addGen(assignment.element, bit)
            }
            dfcxLiveAssignments.addKill(KillFrom.Execution, assignment.element, bit)
        }
        for (declaration in declarations) {
            val bit = pathToIndex[declaration.path] ?: error("No such declaration path in pathToIndex")
            dfcxLivePaths.addKill(KillFrom.ScopeEnd, declaration.element, bit)
        }
    }

    fun addDeclaration(binding: RsPatBinding) {
        val usagePath = UsagePath.Base(binding)
        addUsagePath(usagePath)
        declarations.add(Declaration(usagePath))
    }

    fun addUsage(element: RsElement, cmt: Cmt) {
        val usagePath = UsagePath.computeFor(cmt) ?: return
        if (!pathToIndex.containsKey(usagePath)) return
        usages.add(Usage(usagePath, element))
    }

    fun addAssignment(binding: RsPatBinding, element: RsElement, isInit: Boolean) {
        val usagePath = UsagePath.Base(binding)
        if (isInit) {
            addUsagePath(usagePath)
        }
        if (!pathToIndex.containsKey(usagePath)) return
        assignments.add(Assignment(usagePath, element, isInit))
    }
}
