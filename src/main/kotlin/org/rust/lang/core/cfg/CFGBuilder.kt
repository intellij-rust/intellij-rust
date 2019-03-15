/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.cfg

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.arms
import org.rust.lang.core.psi.ext.isLazy
import org.rust.lang.core.psi.ext.patList
import org.rust.lang.core.types.ty.TyNever
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.core.types.type
import org.rust.lang.utils.Graph
import java.util.*

class CFGBuilder(val graph: Graph<CFGNodeData, CFGEdgeData>, val entry: CFGNode, val exit: CFGNode) : RsVisitor() {
    class BlockScope(val block: RsBlock, val breakNode: CFGNode)

    class LoopScope(val loop: RsExpr, val continueNode: CFGNode, val breakNode: CFGNode)

    enum class ScopeControlFlowKind { BREAK, CONTINUE }

    data class Destination(val label: RsLabel?, val target: RsElement)


    private var result: CFGNode? = null
    private val preds: Deque<CFGNode> = ArrayDeque<CFGNode>()
    private val pred: CFGNode get() = preds.peek()
    private val loopScopes: Deque<LoopScope> = ArrayDeque<LoopScope>()
    private val breakableBlockScopes: Deque<BlockScope> = ArrayDeque<BlockScope>()

    private inline fun finishWith(callable: () -> CFGNode) {
        result = callable()
    }

    private fun finishWith(value: CFGNode) {
        result = value
    }

    private fun finishWithAstNode(element: RsElement, pred: CFGNode) =
        finishWith { addAstNode(element, pred) }

    private inline fun withLoopScope(loopScope: LoopScope, callable: () -> Unit) {
        loopScopes.push(loopScope)
        callable()
        loopScopes.pop()
    }

    private fun addAstNode(element: RsElement, vararg preds: CFGNode): CFGNode =
        addNode(CFGNodeData.AST(element), *preds)

    private fun addDummyNode(vararg preds: CFGNode): CFGNode =
        addNode(CFGNodeData.Dummy, *preds)

    private fun addUnreachableNode(): CFGNode =
        addNode(CFGNodeData.Unreachable)

    private fun addNode(data: CFGNodeData, vararg preds: CFGNode): CFGNode {
        val newNode = graph.addNode(data)
        preds.forEach { addContainedEdge(it, newNode) }
        return newNode
    }

    fun addContainedEdge(source: CFGNode, target: CFGNode) {
        val data = CFGEdgeData(emptyList())
        graph.addEdge(source, target, data)
    }

    private fun addReturningEdge(fromNode: CFGNode) {
        val data = CFGEdgeData(loopScopes.map { it.loop })
        graph.addEdge(fromNode, exit, data)
    }

    private fun straightLine(expr: RsExpr, pred: CFGNode, subExprs: List<RsExpr?>): CFGNode {
        val subExprsExit = subExprs.fold(pred) { acc, subExpr -> process(subExpr, acc) }
        return addAstNode(expr, subExprsExit)
    }

    fun process(element: RsElement?, pred: CFGNode): CFGNode {
        if (element == null) return pred

        result = null
        val oldPredsSize = preds.size
        preds.push(pred)
        element.accept(this)
        preds.pop()
        assert(preds.size == oldPredsSize)

        return checkNotNull(result) { "Processing ended inconclusively" }
    }

    private fun processSubPats(pat: RsPat, subPats: List<RsPat>): CFGNode {
        val patsExit = subPats.fold(pred) { acc, pat -> process(pat, acc) }
        return addAstNode(pat, patsExit)
    }

    private fun processOrPats(orPats: RsOrPats?, pred: CFGNode): CFGNode {
        val pats = orPats?.patList ?: return pred
        val orPatsExit = addDummyNode()
        for (pat in pats) {
            val patExit = process(pat, pred)
            addContainedEdge(patExit, orPatsExit)
        }
        return orPatsExit
    }

    private fun processCall(callExpr: RsExpr, funcOrReceiver: RsExpr?, args: List<RsExpr?>): CFGNode {
        val funcOrReceiverExit = process(funcOrReceiver, pred)
        return straightLine(callExpr, funcOrReceiverExit, args)
    }

    private fun processExpr(expr: RsExpr, parent: RsElement, pred: CFGNode) {
        val exprExit = process(expr, pred)
        if (expr.type is TyNever) {
            addReturningEdge(exprExit)
            finishWith { addUnreachableNode() }
        } else {
            finishWithAstNode(parent, exprExit)
        }
    }

    override fun visitBlock(block: RsBlock) {
        val stmtsExit = block.stmtList.fold(pred) { pred, stmt -> process(stmt, pred) }
        val blockExpr = block.expr ?: return finishWithAstNode(block, stmtsExit)
        processExpr(blockExpr, block, stmtsExit)
    }

    override fun visitLetDecl(letDecl: RsLetDecl) {
        val initExit = process(letDecl.expr, pred)
        val exit = process(letDecl.pat, initExit)

        finishWithAstNode(letDecl, exit)
    }

    override fun visitNamedFieldDecl(fieldDecl: RsNamedFieldDecl) = finishWith(pred)

    override fun visitLabelDecl(labelDecl: RsLabelDecl) = finishWith(pred)

    override fun visitExprStmt(exprStmt: RsExprStmt) =
        processExpr(exprStmt.expr, exprStmt, pred)

    override fun visitPatIdent(patIdent: RsPatIdent) {
        val subPatExit = process(patIdent.pat, pred)
        val bindingExit = process(patIdent.patBinding, subPatExit)
        finishWithAstNode(patIdent, bindingExit)
    }

    override fun visitPatBinding(patBinding: RsPatBinding) =
        finishWithAstNode(patBinding, pred)

    override fun visitPatRange(patRange: RsPatRange) =
        finishWithAstNode(patRange, pred)

    override fun visitPatConst(patConst: RsPatConst) =
        finishWithAstNode(patConst, pred)

    override fun visitPatWild(patWild: RsPatWild) =
        finishWithAstNode(patWild, pred)

    override fun visitPathExpr(pathExpr: RsPathExpr) =
        finishWithAstNode(pathExpr, pred)

    override fun visitRangeExpr(rangeExpr: RsRangeExpr) =
        finishWith { straightLine(rangeExpr, pred, rangeExpr.exprList) }

    override fun visitPatTup(patTup: RsPatTup) =
        finishWith { processSubPats(patTup, patTup.patList) }

    override fun visitPatTupleStruct(patTupleStruct: RsPatTupleStruct) =
        finishWith { processSubPats(patTupleStruct, patTupleStruct.patList) }

    override fun visitPatStruct(patStruct: RsPatStruct) =
        finishWith { processSubPats(patStruct, patStruct.patFieldList.mapNotNull { it.patFieldFull?.pat }) }

    override fun visitPatSlice(patSlice: RsPatSlice) =
        finishWith { processSubPats(patSlice, patSlice.patList) }

    override fun visitBlockExpr(blockExpr: RsBlockExpr) {
        val blockExit = process(blockExpr.block, pred)
        finishWithAstNode(blockExpr, blockExit)
    }

    override fun visitIfExpr(ifExpr: RsIfExpr) {
        //
        //      [pred]             [pred]
        //        |                  |
        //        v 1                v 1
        //      [cond]             [cond]
        //        |                  |
        //       / \                / \
        //      /   \              /   \
        //     v 2   *            v 2   *
        //   [pats]? |          [pats]? |
        //     |     |            |     |
        //     v     v 3          v     |
        //   [then][else]       [then]  |
        //     |     |            |     |
        //     v 4   v 5          v 3   v 4
        //     [ifExpr]          [ifExpr]
        //
        val expr = ifExpr.condition?.expr
        val orPats = ifExpr.condition?.orPats

        val exprExit = process(expr, pred)
        val orPatsExit = processOrPats(orPats, exprExit)

        val thenExit = process(ifExpr.block, orPatsExit)

        val elseBranch = ifExpr.elseBranch

        if (elseBranch != null) {
            val elseExit = process(elseBranch.block, exprExit)
            finishWith { addAstNode(ifExpr, thenExit, elseExit) }
        } else {
            finishWith { addAstNode(ifExpr, exprExit, thenExit) }
        }
    }

    override fun visitWhileExpr(whileExpr: RsWhileExpr) {
        //
        //         [pred]
        //           |
        //           v 1
        //       [loopback] <--+ 5
        //           |         |
        //           v 2       |
        //   +-----[cond]      |
        //   |       |         |
        //   |       v         |
        //   |      [pat]?     |
        //   |       |         |
        //   |       v 4       |
        //   |     [body] -----+
        //   v 3
        // [whileExpr]
        //
        val loopBack = addDummyNode(pred)
        val whileExprExit = addAstNode(whileExpr)
        val loopScope = LoopScope(whileExpr, loopBack, whileExprExit)

        withLoopScope(loopScope) {
            val orPats = whileExpr.condition?.orPats
            val expr = whileExpr.condition?.expr

            val exprExit = process(expr, loopBack)
            addContainedEdge(exprExit, whileExprExit)

            val orPatsExit = processOrPats(orPats, exprExit)

            val bodyExit = process(whileExpr.block, orPatsExit)

            addContainedEdge(bodyExit, loopBack)
        }

        finishWith(whileExprExit)
    }

    override fun visitLoopExpr(loopExpr: RsLoopExpr) {
        //
        //     [pred]
        //       |
        //       v 1
        //   [loopback] <---+
        //       |      4   |
        //       v 3        |
        //     [body] ------+
        //
        //   [loopExpr] 2
        //
        val loopBack = addDummyNode(pred)
        val exprExit = addAstNode(loopExpr)
        val loopScope = LoopScope(loopExpr, loopBack, exprExit)

        withLoopScope(loopScope) {
            val bodyExit = process(loopExpr.block, loopBack)
            addContainedEdge(bodyExit, loopBack)
        }

        finishWith(exprExit)
    }

    override fun visitForExpr(forExpr: RsForExpr) {
        val loopBack = addDummyNode(pred)
        val exprExit = addAstNode(forExpr)
        val loopScope = LoopScope(forExpr, loopBack, exprExit)

        withLoopScope(loopScope) {
            val conditionExit = process(forExpr.expr, loopBack)
            addContainedEdge(conditionExit, exprExit)

            val bodyExit = process(forExpr.block, conditionExit)
            addContainedEdge(bodyExit, loopBack)
        }

        finishWith(exprExit)
    }

    override fun visitBinaryExpr(binaryExpr: RsBinaryExpr) {
        if (binaryExpr.binaryOp.isLazy) {
            val leftExit = process(binaryExpr.left, pred)
            val rightExit = process(binaryExpr.right, leftExit)
            finishWith { addAstNode(binaryExpr, leftExit, rightExit) }
        } else {
            if (binaryExpr.left.type is TyPrimitive) {
                finishWith { straightLine(binaryExpr, pred, listOf(binaryExpr.left, binaryExpr.right)) }
            } else {
                finishWith { processCall(binaryExpr, binaryExpr.left, listOf(binaryExpr.right)) }
            }
        }
    }

    override fun visitRetExpr(retExpr: RsRetExpr) {
        val valueExit = process(retExpr.expr, pred)
        val returnExit = addAstNode(retExpr, valueExit)
        addReturningEdge(returnExit)
        finishWith { addUnreachableNode() }
    }

    // TODO: this cases require regions which are not implemented yet
    override fun visitBreakExpr(breakExpr: RsBreakExpr) = finishWith(pred)

    override fun visitContExpr(contExpr: RsContExpr) = finishWith(pred)

    override fun visitArrayExpr(arrayExpr: RsArrayExpr) =
        finishWith { straightLine(arrayExpr, pred, arrayExpr.exprList) }

    override fun visitCallExpr(callExpr: RsCallExpr) =
        finishWith { processCall(callExpr, callExpr.expr, callExpr.valueArgumentList.exprList) }

    override fun visitIndexExpr(indexExpr: RsIndexExpr) =
        finishWith { processCall(indexExpr, indexExpr.exprList.first(), indexExpr.exprList.drop(1)) }

    override fun visitUnaryExpr(unaryExpr: RsUnaryExpr) =
        finishWith { processCall(unaryExpr, unaryExpr.expr, emptyList()) }

    override fun visitTupleExpr(tupleExpr: RsTupleExpr) =
        finishWith { straightLine(tupleExpr, pred, tupleExpr.exprList) }

    override fun visitStructLiteral(structLiteral: RsStructLiteral) =
        finishWith { straightLine(structLiteral, pred, structLiteral.structLiteralBody.structLiteralFieldList.map { it.expr }) }

    override fun visitCastExpr(castExpr: RsCastExpr) =
        finishWith { straightLine(castExpr, pred, listOf(castExpr.expr)) }

    override fun visitDotExpr(dotExpr: RsDotExpr) {
        val methodCall = dotExpr.methodCall
        if (methodCall == null) {
            finishWith { straightLine(dotExpr, pred, listOf(dotExpr.expr)) }
        } else {
            finishWith { processCall(dotExpr, dotExpr.expr, methodCall.valueArgumentList.exprList) }
        }
    }

    override fun visitLitExpr(litExpr: RsLitExpr) =
        finishWith { straightLine(litExpr, pred, emptyList()) }

    override fun visitMatchExpr(matchExpr: RsMatchExpr) {
        fun processGuard(guard: RsMatchArmGuard, prevGuards: ArrayDeque<CFGNode>, guardStart: CFGNode): CFGNode {
            val guardExit = process(guard, guardStart)

            prevGuards.forEach { addContainedEdge(it, guardStart) }
            prevGuards.clear()
            prevGuards.push(guardExit)

            return guardExit
        }

        val discriminantExit = process(matchExpr.expr, pred)
        val exprExit = addAstNode(matchExpr)

        val prevGuards = ArrayDeque<CFGNode>()

        matchExpr.arms.forEach { arm ->
            val armExit = addDummyNode()
            val guard = arm.matchArmGuard

            arm.patList.forEach { pat ->
                var patExit = process(pat, discriminantExit)
                if (guard != null) {
                    val guardStart = addDummyNode(patExit)
                    patExit = processGuard(guard, prevGuards, guardStart)
                }
                addContainedEdge(patExit, armExit)
            }

            val bodyExit = process(arm.expr, armExit)
            addContainedEdge(bodyExit, exprExit)
        }

        finishWith(exprExit)
    }

    override fun visitMatchArmGuard(guard: RsMatchArmGuard) {
        val conditionExit = process(guard.expr, pred)
        finishWithAstNode(guard, conditionExit)
    }

    override fun visitParenExpr(parenExpr: RsParenExpr) = parenExpr.expr.accept(this)

    override fun visitTryExpr(tryExpr: RsTryExpr) {
        val exprExit = addAstNode(tryExpr)
        val expr = process(tryExpr.expr, pred)
        val checkExpr = addDummyNode(expr)
        addReturningEdge(checkExpr)
        addContainedEdge(expr, exprExit)
        finishWith(exprExit)
    }

    override fun visitElement(element: RsElement) = finishWith(pred)
}
