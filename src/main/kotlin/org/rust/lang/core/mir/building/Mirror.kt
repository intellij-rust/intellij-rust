/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.asSource
import org.rust.lang.core.mir.schemas.MirSourceInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.type

fun RsExpr.mirror(source: MirSourceInfo = this.asSource): ThirExpr {
    val ty = type
    val mirrored = when (this) {
        is RsParenExpr -> expr?.mirror(source) ?: TODO()
        is RsUnaryExpr -> {
            when {
                this.minus != null -> {
                    val clearExpr = expr?.let { unwrapParenExprs(it) } ?: TODO()
                    if (clearExpr is RsLitExpr) {
                        ThirExpr.Literal(clearExpr, true, ty, source)
                    } else {
                        ThirExpr.Unary(UnaryOperator.MINUS, expr?.mirror() ?: TODO(), ty, source)
                    }
                }
                this.excl != null -> ThirExpr.Unary(UnaryOperator.NOT, expr?.mirror() ?: TODO(), ty, source)
                this.mul != null -> TODO() // deref
                else -> throw IllegalStateException("Unexpected unary operator")
            }
        }
        is RsLitExpr -> ThirExpr.Literal(this, false, ty, source)
        is RsBinaryExpr -> {
            when (val operator = this.binaryOp.operatorType) {
                is ArithmeticOp -> ThirExpr.Binary(operator, left.mirror(), right?.mirror() ?: TODO(), ty, source)
                is LogicOp -> ThirExpr.Logical(operator, left.mirror(), right?.mirror() ?: TODO(), ty, source)
                else -> TODO()
            }
        }
        is RsBlockExpr -> ThirExpr.Block(block.mirrorBlock(), ty, source)
        is RsIfExpr -> ThirExpr.If(
            cond = this.condition?.expr?.mirror() ?: TODO(),
            then = this.block?.mirror(type) ?: TODO(),
            `else` = this.elseBranch?.block?.mirror(type),
            ty = ty,
            source = source,
        )
        is RsUnitExpr -> ThirExpr.Tuple(emptyList(), ty, source)
        is RsTupleExpr -> ThirExpr.Tuple(exprList.map { it.mirror() }, ty, source)
        else -> TODO("Not implemented for ${this::class}")
    }
    return ThirExpr.Scope(mirrored, mirrored.ty, source)
    // TODO:
    //  1. there are some adjustments
    //  2. something about destruction scope
}

private fun RsBlock.mirror(ty: Ty): ThirExpr {
    return ThirExpr.Block(this.mirrorBlock(), ty, asSource)
}

private fun RsBlock.mirrorBlock(): ThirBlock {
    val (stmts, expr) = this.expandedStmtsAndTailExpr
    // TODO: mirror [stmts]
    return ThirBlock(
        expr = expr?.mirror() ?: error("Could not get expr from block"),
        source = this.asSource
    )
}
