/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import com.intellij.psi.PsiElement
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
            is RsExprStmt -> expr.cfgText() + ";"
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
                    block.expr?.takeIf { expr ->
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
                val blockTailExpr = parentBlock.expr
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
    class TailStatement(val stmt: RsExprStmt) : ExitPoint()

    companion object {
        fun process(fn: PsiElement?, sink: (ExitPoint) -> Unit) {
            fn?.acceptChildren(ExitPointVisitor(sink))
        }
    }
}

private class ExitPointVisitor(
    private val sink: (ExitPoint) -> Unit
) : RsVisitor() {
    var inTry = 0
    var inEndLoop = 0
    var outerLoopLabel: String? = null

    override fun visitElement(element: RsElement) = element.acceptChildren(this)

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

    override fun visitBreakExpr(breakExpr: RsBreakExpr) {
        when (inEndLoop) {
            0 -> return
            1 -> sink(ExitPoint.TailExpr(breakExpr))
            else -> {
                val label = breakExpr.label ?: return
                if (label.referenceName == outerLoopLabel) {
                    sink(ExitPoint.TailExpr(breakExpr))
                }
            }
        }
    }

    override fun visitExpr(expr: RsExpr) {
        when (expr) {
            is RsIfExpr,
            is RsBlockExpr,
            is RsMatchExpr -> expr.acceptChildren(this)
            is RsLoopExpr ->
                if (expr.isInTailPosition || inEndLoop >= 1) {
                    if (inEndLoop == 0) outerLoopLabel = expr.labelDecl?.name
                    inEndLoop += 1
                    expr.acceptChildren(this)
                    inEndLoop -= 1
                    if (inEndLoop == 0) outerLoopLabel = null
                } else {
                    expr.acceptChildren(this)
                }
            else -> {
                if (expr.isInTailPosition) sink(ExitPoint.TailExpr(expr)) else expr.acceptChildren(this)
            }
        }
    }

    override fun visitExprStmt(exprStmt: RsExprStmt) {
        exprStmt.acceptChildren(this)
        val block = exprStmt.parent as? RsBlock ?: return
        if (block.expr != null) return
        if (block.stmtList.lastOrNull() != exprStmt) return

        val parent = block.parent
        if (isTailStatement(parent, exprStmt) && inEndLoop == 0) {
            sink(ExitPoint.TailStatement(exprStmt))
        }
    }

    private fun isTailStatement(parent: PsiElement?, exprStmt: RsExprStmt) =
        (parent is RsFunction || parent is RsExpr && parent.isInTailPosition) && exprStmt.expr.type != TyNever

    private val RsExpr.isInTailPosition: Boolean
        get() {
            // Ignore the expression if any of its ancestors are cfg-disabled
            if (!existsAfterExpansion) return false

            for (ancestor in ancestors) {
                when (ancestor) {
                    // RsExprStmt may actually be RsExpr because of its rightSiblings' cfg attributes
                    is RsExprStmt -> return ancestor.semicolon == null
                    is RsFunction, is RsLambdaExpr -> return true
                    is RsStmt, is RsCondition, is RsMatchArmGuard, is RsPat, is RsMacroArgument -> return false
                    else -> {
                        val parent = ancestor.parent
                        if ((ancestor is RsExpr && parent is RsMatchExpr) || parent is RsLoopExpr)
                            return false
                    }
                }
            }
            return false
        }
}

private fun RsExpr.markNeverTypeAsExit(sink: (ExitPoint) -> Unit) {
    if (this.type == TyNever) sink(ExitPoint.DivergingExpr(this))
}
