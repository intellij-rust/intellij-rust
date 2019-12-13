/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.cfg

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.regions.ScopeTree
import org.rust.lang.core.types.ty.TyNever
import org.rust.lang.core.types.type
import org.rust.lang.utils.Edge
import org.rust.lang.utils.Node
import org.rust.lang.utils.PresentableGraph
import org.rust.lang.utils.PresentableNodeData

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
            is AST -> element?.cfgText()?.trim() ?: "AST"
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
            is RsExprStmt -> expr.cfgText() + ";"
            else -> this.text
        }
}

class CFGEdgeData(val exitingScopes: List<RsElement>)

typealias CFGNode = Node<CFGNodeData, CFGEdgeData>
typealias CFGEdge = Edge<CFGNodeData, CFGEdgeData>

class ControlFlowGraph private constructor(
    val owner: RsElement,
    val graph: PresentableGraph<CFGNodeData, CFGEdgeData>,
    val body: RsBlock,
    val regionScopeTree: ScopeTree,
    val entry: CFGNode,
    val exit: CFGNode,
    val termination: CFGNode
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

            return ControlFlowGraph(owner, graph, body, regionScopeTree, entry, fnExit, termination)
        }
    }

    fun isNodeReachable(item: RsElement) = graph.depthFirstTraversal(entry).any { it.data.element == item }

    fun buildLocalIndex(): HashMap<RsElement, MutableList<CFGNode>> {
        val table = hashMapOf<RsElement, MutableList<CFGNode>>()
        val func = body.parent

        if (func is RsFunction) {
            val formals = object : RsVisitor() {
                override fun visitPat(pat: RsPat) {
                    table.getOrPut(pat, ::mutableListOf).add(entry)
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
        val macroCall = macroExpr.macroCall
        if (macroCall.macroName == "try"
            && macroCall.exprMacroArgument != null
            && inTry == 0) sink(ExitPoint.TryExpr(macroExpr))

        if (macroExpr.type == TyNever) sink(ExitPoint.DivergingExpr(macroExpr))
    }

    override fun visitExpr(expr: RsExpr) {
        when (expr) {
            is RsIfExpr,
            is RsBlockExpr,
            is RsMatchExpr -> expr.acceptChildren(this)
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
        if (isTailStatement(parent, exprStmt)) {
            sink(ExitPoint.TailStatement(exprStmt))
        }
    }

    private fun isTailStatement(parent: PsiElement?, exprStmt: RsExprStmt) =
        (parent is RsFunction || parent is RsExpr && parent.isInTailPosition) && exprStmt.expr.type != TyNever

    private val RsExpr.isInTailPosition: Boolean
        get() {
            for (ancestor in ancestors) {
                when (ancestor) {
                    is RsFunction, is RsLambdaExpr -> return true
                    is RsStmt, is RsCondition, is RsMatchArmGuard, is RsPat -> return false
                    else -> if (ancestor is RsExpr && ancestor.parent is RsMatchExpr) return false
                }
            }
            return false
        }
}
