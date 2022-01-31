/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.regions.ScopeTree
import org.rust.lang.core.types.ty.TyNever
import org.rust.lang.core.types.type
import org.rust.lang.utils.Node
import org.rust.lang.utils.PresentableGraph
import org.rust.lang.utils.PresentableNodeData
import org.rust.stdext.buildSet

sealed class CFGNodeData(val element: RsElement? = null) : PresentableNodeData {

    /** Any execution unit (e.g. expression, statement) */
    class AST(element: RsElement) : CFGNodeData(element)

    /** Execution start */
    object Entry : CFGNodeData()

    /** Normal execution end (e.g. after `main` function block) */
    object Exit : CFGNodeData()

    /** Any real (e.g. `panic!()` call) or imaginary (e.g. after infinite loop) execution end */
    object Termination : CFGNodeData()

    /** Supplementary node to build complex control flow (e.g. loops and pattern matching) */
    object Dummy : CFGNodeData()

    /** Start of any unreachable code (e.g. after `return`) */
    object Unreachable : CFGNodeData()


    override val text: String
        get() = when (this) {
            is AST -> element!!.cfgText().trim()
            Entry -> "Entry"
            Exit -> "Exit"
            Termination -> "Termination"
            Dummy -> "Dummy"
            Unreachable -> "Unreachable"
        }

    private fun RsElement.cfgText(): String =
        when (this) {
            is RsBlock, is RsBlockExpr -> "BLOCK"
            is RsIfExpr -> "IF"
            is RsWhileExpr -> "WHILE"
            is RsLoopExpr -> "LOOP"
            is RsForExpr -> "FOR"
            is RsMatchExpr -> "MATCH"
            is RsLambdaExpr -> "CLOSURE"
            is RsExprStmt -> outerAttrList.joinToString(postfix = " ") { it.text } + expr.cfgText() + ";"
            else -> this.text
        }
}

class CFGEdgeData(val exitingScopes: List<RsElement>)

typealias CFGNode = Node<CFGNodeData, CFGEdgeData>
typealias CFGGraph = PresentableGraph<CFGNodeData, CFGEdgeData>

class ControlFlowGraph private constructor(
    val owner: RsElement,
    val graph: CFGGraph,
    val body: RsBlock,
    val regionScopeTree: ScopeTree,
    val entry: CFGNode,
    val exit: CFGNode,
    val unreachableElements: Set<RsElement>
) {
    companion object {
        fun buildFor(body: RsBlock, regionScopeTree: ScopeTree): ControlFlowGraph {
            val owner = body.parent as RsElement
            val graph = PresentableGraph<CFGNodeData, CFGEdgeData>()
            val entry = graph.addNode(CFGNodeData.Entry)
            val fnExit = graph.addNode(CFGNodeData.Exit)
            val termination = graph.addNode(CFGNodeData.Termination)

            val cfgBuilder = CFGBuilder(regionScopeTree, graph, entry, fnExit, termination)
            val bodyExit = cfgBuilder.process(body, entry)
            cfgBuilder.addContainedEdge(bodyExit, fnExit)
            cfgBuilder.addContainedEdge(fnExit, termination)

            val unreachableElements = collectUnreachableElements(graph, entry)

            return ControlFlowGraph(owner, graph, body, regionScopeTree, entry, fnExit, unreachableElements)
        }

        /**
         * Collects unreachable elements, i.e. elements cannot be reached by execution of [body].
         *
         * Only collects [RsExprStmt]s, [RsLetDecl]s and tail expressions, ignoring conditionally disabled.
         */
        private fun collectUnreachableElements(graph: CFGGraph, entry: CFGNode): Set<RsElement> {
            /**
             * In terms of our control-flow graph, a [RsElement]'s node is not reachable if it cannot be fully executed,
             * since [CFGBuilder] processes elements in post-order.
             * But partially executed elements should not be treated as unreachable because the execution
             * actually reaches them; only some parts of such elements should be treated as unreachable instead.
             *
             * So, first of all, collect all the unexecuted [RsElement]s (including partially executed)
             */
            val unexecutedElements = buildSet<RsElement> {
                val fullyExecutedNodeIndices = graph.depthFirstTraversal(entry).map { it.index }.toSet()
                graph.forEachNode { node ->
                    if (node.index !in fullyExecutedNodeIndices) {
                        val element = node.data.element ?: return@forEachNode
                        add(element)
                    }
                }
            }
            val unexecutedStmts = unexecutedElements
                .filterIsInstance<RsStmt>()
            val unexecutedTailExprs = unexecutedElements
                .filterIsInstance<RsBlock>()
                .mapNotNull { block ->
                    block.expandedTailExpr?.takeIf { expr ->
                        when (expr) {
                            is RsMacroExpr -> expr.macroCall in unexecutedElements
                            else -> expr in unexecutedElements
                        }
                    }
                }

            /**
             * If [unexecuted] produces termination by itself
             * (which means its inner expression type is [TyNever], see the corresponding checks below),
             * it should be treated as unreachable only if the execution does not reach the beginning of [unexecuted].
             * The latter means that previous statement is not fully executed.
             * This is needed in order not to treat the first, basically reachable, `return` as unreachable
             */
            fun isUnreachable(unexecuted: RsElement, innerExpr: RsExpr?): Boolean {
                // Ignore conditionally disabled elements
                if (!unexecuted.existsAfterExpansion) {
                    return false
                }
                if (innerExpr != null && !innerExpr.existsAfterExpansion) {
                    return false
                }

                // Unexecuted element is definitely unreachable if its inner expression does not produce termination
                if (innerExpr != null && innerExpr.type !is TyNever) {
                    return true
                }

                // Otherwise, unexecuted element's child produces termination and therefore
                // we should check if it is really unreachable or just not fully executed
                val parentBlock = unexecuted.ancestorStrict<RsBlock>() ?: return false
                val blockStmts = parentBlock.stmtList.takeIf { it.isNotEmpty() } ?: return false
                val blockTailExpr = parentBlock.expandedTailExpr
                val index = blockStmts.indexOf(unexecuted)
                return when {
                    index >= 1 -> {
                        // `{ ... <unreachable>; <element>; ... }`
                        blockStmts[index - 1] in unexecutedElements
                    }
                    unexecuted == blockTailExpr -> {
                        // `{ ... <unreachable>; <element> }`
                        blockStmts.last() in unexecutedElements
                    }
                    else -> false
                }
            }

            val unreachableElements = mutableSetOf<RsElement>()

            for (stmt in unexecutedStmts) {
                when {
                    stmt is RsExprStmt && isUnreachable(stmt, stmt.expr) -> {
                        unreachableElements.add(stmt)
                    }
                    stmt is RsLetDecl && isUnreachable(stmt, stmt.expr) -> {
                        unreachableElements.add(stmt)
                    }
                }
            }
            for (tailExpr in unexecutedTailExprs) {
                if (isUnreachable(tailExpr, tailExpr)) {
                    unreachableElements.add(tailExpr)
                }
            }

            return unreachableElements
        }
    }

    fun buildLocalIndex(): HashMap<RsElement, MutableList<CFGNode>> {
        val table = hashMapOf<RsElement, MutableList<CFGNode>>()
        val func = body.parent

        if (func is RsFunction) {
            val formals = object : RsVisitor() {
                override fun visitPatBinding(binding: RsPatBinding) {
                    table.getOrPut(binding, ::mutableListOf).add(entry)
                }

                override fun visitPatField(field: RsPatField) {
                    field.acceptChildren(this)
                }

                override fun visitPat(pat: RsPat) {
                    pat.acceptChildren(this)
                }
            }

            func.valueParameters.mapNotNull { it.pat }.forEach { formals.visitPat(it) }
        }

        graph.forEachNode { node ->
            val element = node.data.element
            if (element != null) table.getOrPut(element, ::mutableListOf).add(node)
        }

        return table
    }
}


sealed class ExitPoint {
    class Return(val e: RsRetExpr) : ExitPoint()
    class TryExpr(val e: RsExpr) : ExitPoint() // `?` or `try!`
    class DivergingExpr(val e: RsExpr) : ExitPoint()
    class TailExpr(val e: RsExpr) : ExitPoint()

    /**
     * ```
     * fn foo() -> i32 {
     *     0; // invalid tail statement
     * }
     * ```
     *
     * This is not a real exit point. Used in [org.rust.ide.inspections.RsExtraSemicolonInspection]
     */
    class InvalidTailStatement(val stmt: RsExprStmt) : ExitPoint()

    companion object {
        fun process(fn: RsFunctionOrLambda, sink: (ExitPoint) -> Unit) {
            when (fn) {
                is RsFunction -> process(fn.block ?: return, sink)
                is RsLambdaExpr -> when (val expr = fn.expr ?: return) {
                    is RsBlockExpr -> process(expr.block, sink)
                    else -> processTailExpr(expr, sink)
                }
            }
        }

        fun process(block: RsBlock, sink: (ExitPoint) -> Unit) {
            block.acceptChildren(ExitPointVisitor(sink))
            processBlockTailExprs(block, sink)
        }

        private fun processBlockTailExprs(block: RsBlock, sink: (ExitPoint) -> Unit) {
            val (stmts, tailExpr) = block.expandedStmtsAndTailExpr
            if (tailExpr != null) {
                processTailExpr(tailExpr, sink)
            } else {
                val lastStmt = stmts.lastOrNull()
                if (lastStmt is RsExprStmt && lastStmt.hasSemicolon && lastStmt.expr.type != TyNever) {
                    sink(InvalidTailStatement(lastStmt))
                }
            }
        }

        private fun processTailExpr(expr: RsExpr, sink: (ExitPoint) -> Unit) {
            when (expr) {
                is RsBlockExpr -> {
                    if (expr.isTry || expr.isAsync) {
                        sink(TailExpr(expr))
                        return
                    }
                    val label = expr.labelDecl?.name
                    if (label != null) {
                        expr.processBreakExprs(label, true) {
                            sink(TailExpr(it))
                        }
                    }
                    processBlockTailExprs(expr.block, sink)
                }
                is RsIfExpr -> {
                    val ifBlock = expr.block
                    if (ifBlock != null) {
                        processBlockTailExprs(ifBlock, sink)
                    }
                    val elseBranch = expr.elseBranch ?: return
                    val elseBranchBlock = elseBranch.block
                    if (elseBranchBlock != null) {
                        processBlockTailExprs(elseBranchBlock, sink)
                    }
                    val elseIf = elseBranch.ifExpr
                    if (elseIf != null) {
                        processTailExpr(elseIf, sink)
                    }
                }
                is RsMatchExpr -> {
                    for (arm in expr.matchBody?.matchArmList.orEmpty()) {
                        val armExpr = arm.expr ?: continue
                        processTailExpr(armExpr, sink)
                    }
                }
                is RsLoopExpr -> {
                    expr.processBreakExprs(expr.labelDecl?.name, false) {
                        sink(TailExpr(it))
                    }
                }
                else -> sink(TailExpr(expr))
            }
        }
    }
}

private class ExitPointVisitor(
    private val sink: (ExitPoint) -> Unit
) : RsRecursiveVisitor() {
    var inTry = 0

    override fun visitLambdaExpr(lambdaExpr: RsLambdaExpr) = Unit
    override fun visitFunction(function: RsFunction) = Unit
    override fun visitBlockExpr(blockExpr: RsBlockExpr) {
        when {
            blockExpr.isTry -> {
                inTry += 1
                super.visitBlockExpr(blockExpr)
                inTry -= 1
            }
            !blockExpr.isAsync -> super.visitBlockExpr(blockExpr)
        }
    }

    override fun visitRetExpr(retExpr: RsRetExpr) = sink(ExitPoint.Return(retExpr))

    override fun visitTryExpr(tryExpr: RsTryExpr) {
        tryExpr.expr.acceptChildren(this)
        if (inTry == 0) sink(ExitPoint.TryExpr(tryExpr))
    }

    override fun visitMacroExpr(macroExpr: RsMacroExpr) {
        super.visitMacroExpr(macroExpr)

        val macroCall = macroExpr.macroCall
        if (macroCall.isStdTryMacro && inTry == 0) {
            sink(ExitPoint.TryExpr(macroExpr))
        }

        macroExpr.markNeverTypeAsExit(sink)
    }

    override fun visitCallExpr(callExpr: RsCallExpr) {
        super.visitCallExpr(callExpr)
        callExpr.markNeverTypeAsExit(sink)
    }

    override fun visitDotExpr(dotExpr: RsDotExpr) {
        super.visitDotExpr(dotExpr)
        dotExpr.markNeverTypeAsExit(sink)
    }
}

private fun RsExpr.markNeverTypeAsExit(sink: (ExitPoint) -> Unit) {
    if (this.type == TyNever) sink(ExitPoint.DivergingExpr(this))
}
