/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.borrowck

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByReference
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByValue
import org.rust.lang.core.types.borrowck.ConsumeMode.Copy
import org.rust.lang.core.types.borrowck.ConsumeMode.Move
import org.rust.lang.core.types.borrowck.MatchMode.CopyingMatch
import org.rust.lang.core.types.borrowck.MatchMode.NonBindingMatch
import org.rust.lang.core.types.borrowck.MoveReason.DirectRefMove
import org.rust.lang.core.types.borrowck.MoveReason.PatBindingMove
import org.rust.lang.core.types.infer.Categorization
import org.rust.lang.core.types.infer.Cmt
import org.rust.lang.core.types.infer.MemoryCategorizationContext
import org.rust.lang.core.types.infer.MutabilityCategory
import org.rust.lang.core.types.regions.ReScope
import org.rust.lang.core.types.regions.Scope
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyFunction
import org.rust.lang.core.types.ty.isMovesByDefault
import org.rust.lang.core.types.type

interface Delegate {
    /** The value found at [cmt] is either copied or moved, depending on mode */
    fun consume(element: RsElement, cmt: Cmt, mode: ConsumeMode)

    /**
     * The value found at [cmt] has been determined to match the pattern [pat], and its subparts are being
     * copied or moved depending on [mode].  Note that [matchedPat] is called on all variant/structs in the pattern
     * (i.e., the interior nodes of the pattern's tree structure) while [consumePat] is called on the binding
     * identifiers in the pattern
     */
    fun matchedPat(pat: RsPat, cmt: Cmt, mode: MatchMode)

    /**
     * The value found at [cmt] is either copied or moved via the
     * pattern binding [consumePat], depending on mode.
     */
    fun consumePat(pat: RsPat, cmt: Cmt, mode: ConsumeMode)

    /** The local variable [element] is declared but not initialized */
    fun declarationWithoutInit(element: RsElement)

    /** The path at [assigneeCmt] is being assigned to */
    fun mutate(assignmentElement: RsElement, assigneeCmt: Cmt, mode: MutateMode)
}

sealed class ConsumeMode {
    object Copy : ConsumeMode()                          // reference to `x` where `x` has a type that copies
    class Move(val reason: MoveReason) : ConsumeMode()   // reference to `x` where x has a type that moves

    val matchMode: MatchMode
        get() = when (this) {
            is Copy -> MatchMode.CopyingMatch
            is Move -> MatchMode.MovingMatch
        }
}

enum class MoveReason {
    DirectRefMove,
    PatBindingMove,
    CaptureMove
}

enum class MatchMode {
    NonBindingMatch,
    BorrowingMatch,
    CopyingMatch,
    MovingMatch,
}

sealed class TrackMatchMode {
    object Unknown : TrackMatchMode()
    class Definite(val mode: MatchMode) : TrackMatchMode()
    object Conflicting : TrackMatchMode()

    val matchMode: MatchMode
        get() = when (this) {
            is Unknown -> MatchMode.NonBindingMatch
            is Definite -> mode
            is Conflicting -> MatchMode.MovingMatch
        }

    fun leastUpperBound(mode: MatchMode): TrackMatchMode =
        when {
            this is Unknown -> Definite(mode)
            this is Definite && this.mode == mode -> this
            this is Definite && mode == NonBindingMatch -> this
            this is Definite && this.mode == NonBindingMatch -> Definite(mode)
            this is Definite && mode == CopyingMatch -> this
            this is Definite && this.mode == CopyingMatch -> Definite(mode)
            this is Definite -> Conflicting
            this is Conflicting -> this
            else -> this
        }
}

enum class MutateMode {
    Init,
    JustWrite,      // e.g. `x = y`
    WriteAndRead    // e.g. `x += y`
}

class ExprUseWalker(private val delegate: Delegate, private val mc: MemoryCategorizationContext) {
    fun consumeBody(body: RsBlock) {
        val function = body.parent as? RsFunction ?: return

        for (parameter in function.valueParameters) {
            val parameterType = parameter.typeReference?.type ?: continue
            val parameterPat = parameter.pat ?: continue

            val bodyScopeRegion = ReScope(Scope.Node(body))
            val parameterCmt = mc.processRvalue(parameter, bodyScopeRegion, parameterType)

            walkIrrefutablePat(parameterCmt, parameterPat)
        }

        walkBlock(body)
    }

    private fun delegateConsume(element: RsElement, cmt: Cmt) {
        val mode = copyOrMove(mc, cmt, DirectRefMove)
        delegate.consume(element, cmt, mode)
    }

    private fun consumeExprs(exprs: List<RsExpr>) =
        exprs.forEach { consumeExpr(it) }

    private fun consumeExpr(expr: RsExpr) {
        val cmt = mc.processExpr(expr)
        delegateConsume(expr, cmt)
        walkExpr(expr)
    }

    private fun mutateExpr(assignmentExpr: RsExpr, expr: RsExpr, mode: MutateMode) {
        val cmt = mc.processExpr(expr)
        delegate.mutate(assignmentExpr, cmt, mode)
        walkExpr(expr)
    }

    private fun selectFromExpr(expr: RsExpr) =
        walkExpr(expr)

    private fun walkExpr(expr: RsExpr) {
        when (expr) {
            is RsUnaryExpr -> {
                val base = expr.expr ?: return
                if (expr.mul != null) {
                    selectFromExpr(base)
                } else if (expr.and == null) {
                    consumeExpr(base)
                }
            }

            is RsDotExpr -> {
                val base = expr.expr
                val fieldLookup = expr.fieldLookup
                val methodCall = expr.methodCall

                if (fieldLookup != null) {
                    selectFromExpr(base)
                } else if (methodCall != null) {
                    consumeExprs(methodCall.valueArgumentList.exprList)
                }
            }

            is RsIndexExpr -> {
                expr.containerExpr?.let { selectFromExpr(it) }
                expr.indexExpr?.let { consumeExpr(it) }
            }

            is RsCallExpr -> {
                walkCallee(expr.expr)
                consumeExprs(expr.valueArgumentList.exprList)
            }

            is RsStructLiteral -> {
                walkStructExpr(expr.structLiteralBody.structLiteralFieldList, expr.structLiteralBody.expr)
            }

            is RsTupleExpr -> {
                consumeExprs(expr.exprList)
            }

            is RsIfExpr -> {
                expr.condition?.let { walkCondition(it) }
                expr.block?.let { walkBlock(it) }
                expr.elseBranch?.block?.let { walkBlock(it) }
            }

            is RsMatchExpr -> {
                val discriminant = expr.expr ?: return
                val discriminantCmt = mc.processExpr(discriminant)

                val arms = expr.matchBody?.matchArmList ?: return
                for (arm in arms) {
                    val mode = armMoveMode(discriminantCmt, arm).matchMode
                    walkArm(discriminantCmt, arm, mode)
                }
            }

            is RsArrayExpr -> consumeExprs(expr.exprList)

            is RsLoopExpr -> expr.block?.let { walkBlock(it) }

            is RsWhileExpr -> {
                expr.condition?.let { walkCondition(it) }
                expr.block?.let { walkBlock(it) }
            }

            is RsBinaryExpr -> {
                val left = expr.left
                val right = expr.right ?: return
                val operator = expr.binaryOp.operatorType
                when (operator) {
                    is AssignmentOp -> mutateExpr(expr, left, MutateMode.JustWrite)
                    is ArithmeticAssignmentOp -> mutateExpr(expr, left, MutateMode.WriteAndRead)
                    else -> consumeExpr(left)
                }
                consumeExpr(right)
            }

            is RsBlockExpr -> walkBlock(expr.block)

            is RsBreakExpr -> expr.expr?.let { consumeExpr(it) }

            is RsRetExpr -> expr.expr?.let { consumeExpr(it) }

            is RsCastExpr -> consumeExpr(expr.expr)
        }
    }

    private fun walkCallee(callee: RsExpr) {
        if (callee.type is TyFunction) consumeExpr(callee)
    }

    private fun walkStmt(stmt: RsStmt) {
        when (stmt) {
            is RsLetDecl -> walkLet(stmt)
            is RsExprStmt -> consumeExpr(stmt.expr)
        }
    }

    private fun walkLet(declaration: RsLetDecl) {
        val init = declaration.expr
        val pat = declaration.pat ?: return
        if (init != null) {
            walkExpr(init)
            val initCmt = mc.processExpr(init)
            walkIrrefutablePat(initCmt, pat)
        } else {
            pat.descendantsOfType<RsPatBinding>().forEach { delegate.declarationWithoutInit(it) }
        }
    }

    private fun walkCondition(condition: RsCondition) {
        val init = condition.expr
        walkExpr(init)
        val initCmt = mc.processExpr(init)
        condition.pat?.let { walkIrrefutablePat(initCmt, it) }
    }

    private fun walkBlock(block: RsBlock) {
        block.stmtList.forEach { walkStmt(it) }
        block.expr?.let { consumeExpr(it) }
    }

    private fun walkStructExpr(fields: List<RsStructLiteralField>, withExpr: RsExpr?) {
        fields.mapNotNull { it.expr }.forEach { consumeExpr(it) }
        if (withExpr == null) return

        val withCmt = mc.processExpr(withExpr)
        val withType = withCmt.ty
        if (withType is TyAdt) {
            val structFields = (withType.item as? RsStructItem)?.namedFields.orEmpty()
            for (field in structFields) {
                val isMentioned = fields.any { it.identifier.text == field.identifier.text }
                if (!isMentioned) {
                    val interior = Categorization.Interior.Field(withCmt, field.name)
                    val fieldCmt = Cmt(withExpr, interior, withCmt.mutabilityCategory.inherit(), withType)
                    delegateConsume(withExpr, fieldCmt)
                }
            }
        }

        walkExpr(withExpr)
    }

    private fun armMoveMode(discriminantCmt: Cmt, arm: RsMatchArm): TrackMatchMode {
        var mode: TrackMatchMode = TrackMatchMode.Unknown
        arm.patList.forEach { mode = determinePatMoveMode(discriminantCmt, it, mode) }
        return mode
    }

    private fun walkArm(discriminantCmt: Cmt, arm: RsMatchArm, mode: MatchMode) {
        arm.patList.forEach { walkPat(discriminantCmt, it, mode) }
        arm.matchArmGuard?.let { consumeExpr(it.expr) }
        arm.expr?.let { consumeExpr(it) }
    }

    private fun walkIrrefutablePat(discriminantCmt: Cmt, pat: RsPat) {
        val mode = determinePatMoveMode(discriminantCmt, pat, TrackMatchMode.Unknown)
        walkPat(discriminantCmt, pat, mode.matchMode)
    }

    /** Identifies any bindings within [pat] whether the overall pattern/match structure is a move, copy, or borrow */
    private fun determinePatMoveMode(discriminantCmt: Cmt, pat: RsPat, mode: TrackMatchMode): TrackMatchMode {
        var newMode = mode
        mc.walkPat(discriminantCmt, pat) { subPatCmt, subPat ->
            if (subPat is RsPatIdent && subPat.patBinding.reference.resolve()?.isConstantLike != true) {
                newMode = when (subPat.patBinding.kind) {
                    is BindByReference -> newMode.leastUpperBound(MatchMode.BorrowingMatch)
                    is BindByValue -> newMode.leastUpperBound(copyOrMove(mc, subPatCmt, PatBindingMove).matchMode)
                }
            }
        }

        return newMode
    }

    /**
     * The core driver for walking a pattern; [matchMode] must be established up front, e.g. via [determinePatMoveMode]
     * (see also [walkIrrefutablePat] for patterns that stand alone)
     */
    private fun walkPat(discriminantCmt: Cmt, pat: RsPat, matchMode: MatchMode) {
        mc.walkPat(discriminantCmt, pat) { subPatCmt, subPat ->
            if (subPat is RsPatIdent && subPat.patBinding.reference.resolve()?.isConstantLike != true) {
                val binding = subPat.patBinding
                val mutabilityCategory = MutabilityCategory.from(binding.mutability)
                val bindingCmt = Cmt(binding, Categorization.Local(binding), mutabilityCategory, binding.type)

                // Each match binding is effectively an assignment to the binding being produced.
                delegate.mutate(subPat, bindingCmt, MutateMode.Init)

                // It is also a borrow or copy/move of the value being matched.
                if (binding.kind is BindByValue) {
                    delegate.consumePat(subPat, subPatCmt, copyOrMove(mc, subPatCmt, PatBindingMove))
                }
            }
        }
    }
}

fun copyOrMove(mc: MemoryCategorizationContext, cmt: Cmt, moveReason: MoveReason): ConsumeMode =
    if (cmt.ty.isMovesByDefault(mc.lookup)) Move(moveReason) else Copy
