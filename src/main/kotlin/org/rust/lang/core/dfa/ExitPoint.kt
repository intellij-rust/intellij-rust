/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.block
import org.rust.lang.core.types.ty.TyNever
import org.rust.lang.core.types.type

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
