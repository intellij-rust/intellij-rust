/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.borrowck

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByReference
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByValue
import org.rust.lang.core.resolve.VALUES
import org.rust.lang.core.types.GatherLivenessContext
import org.rust.lang.core.types.borrowck.ConsumeMode.Copy
import org.rust.lang.core.types.borrowck.ConsumeMode.Move
import org.rust.lang.core.types.borrowck.MatchMode.*
import org.rust.lang.core.types.borrowck.MoveReason.DirectRefMove
import org.rust.lang.core.types.borrowck.MoveReason.PatBindingMove
import org.rust.lang.core.types.infer.Categorization.Interior
import org.rust.lang.core.types.infer.Categorization.Local
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

    /** The value found at [cmt] is either copied or moved via the pattern binding [consumePat], depending on mode */
    fun consumePat(pat: RsPat, cmt: Cmt, mode: ConsumeMode)

    /** The local variable [binding] is declared but not initialized */
    fun declarationWithoutInit(binding: RsPatBinding)

    /** The path at [assigneeCmt] is being assigned to */
    fun mutate(assignmentElement: RsElement, assigneeCmt: Cmt, mode: MutateMode)

    fun useElement(element: RsElement, cmt: Cmt)
}

sealed class ConsumeMode {
    object Copy : ConsumeMode()                          // reference to `x` where `x` has a type that copies
    class Move(val reason: MoveReason) : ConsumeMode()   // reference to `x` where x has a type that moves

    val matchMode: MatchMode
        get() = when (this) {
            is Copy -> CopyingMatch
            is Move -> MovingMatch
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
    NonConsumingMatch,
}

sealed class TrackMatchMode {
    object Unknown : TrackMatchMode()
    class Definite(val mode: MatchMode) : TrackMatchMode()
    object Conflicting : TrackMatchMode()

    val matchMode: MatchMode
        get() = when (this) {
            is Unknown -> NonBindingMatch
            is Definite -> mode
            is Conflicting -> MovingMatch
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

    private fun usePath(pathExpr: RsPathExpr) {
        val cmt = mc.processExpr(pathExpr)
        delegate.useElement(pathExpr, cmt)
    }

    private fun useAllPaths(element: RsElement) {
        (element as? RsPathExpr)?.let { usePath(it) }
        for (path in element.descendantsOfType<RsPathExpr>()) {
            usePath(path)
        }
    }

    private fun useMacroBodyIdent(ident: RsMacroBodyIdent) {
        val declaration = ident.referenceName
            ?.let { ident.findInScope(it, VALUES) } as? RsPatBinding
            ?: return

        val mutability = declaration.mutability
        val type = declaration.type
        val cmt = Cmt(
            ident,
            Local(declaration),
            MutabilityCategory.from(mutability),
            type
        )
        delegate.useElement(ident, cmt)
    }

    private fun useAllMacroBodyIdents(element: RsElement) {
        val idents = element.descendantsOfType<RsMacroBodyIdent>()
        for (ident in idents) {
            useMacroBodyIdent(ident)
        }
    }

    private fun mutateExpr(assignmentExpr: RsExpr, expr: RsExpr, mode: MutateMode) {
        val cmt = mc.processExpr(expr)
        delegate.mutate(assignmentExpr, cmt, mode)
        walkExpr(expr)
    }

    private fun selectFromExpr(expr: RsExpr) {
        walkExpr(expr)
        useAllPaths(expr)
    }

    private fun walkExpr(expr: RsExpr) {
        when (expr) {
            is RsUnaryExpr -> {
                val base = expr.expr ?: return
                when {
                    expr.mul != null -> selectFromExpr(base) // `*foo`
                    expr.and != null -> selectFromExpr(base) // `&foo`
                    else -> consumeExpr(base) // `-foo`, `!foo`, ...
                }
            }

            is RsDotExpr -> {
                val base = expr.expr
                val fieldLookup = expr.fieldLookup
                val methodCall = expr.methodCall

                if (fieldLookup != null) {
                    selectFromExpr(base)
                } else if (methodCall != null) {
                    selectFromExpr(base)
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
                expr.elseBranch?.ifExpr?.let { walkExpr(it) }
                expr.elseBranch?.block?.let { walkBlock(it) }
            }

            is RsMatchExpr -> {
                val discriminant = expr.expr ?: return
                val discriminantCmt = mc.processExpr(discriminant)

                selectFromExpr(discriminant)

                for (arm in expr.arms) {
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

            is RsForExpr -> {
                val init = expr.expr
                val pat = expr.pat
                if (init != null && pat != null) {
                    walkExpr(init)
                    val initCmt = mc.processExpr(init)
                    walkPat(initCmt, pat, NonConsumingMatch)
                }
                expr.block?.let { walkBlock(it) }
            }

            is RsBinaryExpr -> {
                val left = expr.left
                val right = expr.right ?: return
                when (expr.binaryOp.operatorType) {
                    is ArithmeticAssignmentOp -> mutateExpr(expr, left, MutateMode.WriteAndRead)
                    is AssignmentOp -> mutateExpr(expr, left, MutateMode.JustWrite)
                    else -> consumeExpr(left)
                }
                consumeExpr(right)
            }

            is RsLambdaExpr -> expr.expr?.let { walkExpr(it) }

            is RsBlockExpr -> walkBlock(expr.block)

            is RsBreakExpr -> expr.expr?.let { consumeExpr(it) }

            is RsRetExpr -> expr.expr?.let { consumeExpr(it) }

            is RsCastExpr -> consumeExpr(expr.expr)

            is RsParenExpr -> when (expr.parent) {
                is RsDotExpr -> walkExpr(expr.expr)
                else -> consumeExpr(expr.expr)
            }

            is RsTryExpr -> walkExpr(expr.expr)

            is RsMacroExpr -> walkMacroCall(expr.macroCall)

            is RsPathExpr -> usePath(expr)

            is RsRangeExpr -> expr.exprList.forEach(::walkExpr)
        }
    }

    private fun walkCallee(callee: RsExpr) {
        when (callee.type) {
            is TyFunction -> consumeExpr(callee)
            else -> useAllPaths(callee)
        }
    }

    private fun walkMacroCall(macroCall: RsMacroCall) {
        when (val argument = macroCall.macroArgumentElement) {
            is RsExprMacroArgument -> argument.expr?.let(::walkExpr)
            is RsIncludeMacroArgument -> argument.expr?.let(::walkExpr)

            is RsConcatMacroArgument -> argument.exprList.forEach(::walkExpr)
            is RsEnvMacroArgument -> argument.exprList.forEach(::walkExpr)
            is RsVecMacroArgument -> argument.exprList.forEach(::walkExpr)

            is RsFormatMacroArgument -> {
                argument.formatMacroArgList.map { it.expr }.forEach(::walkExpr)
            }
            is RsLogMacroArgument -> {
                argument.expr?.let(::walkExpr)
                argument.formatMacroArgList.map { it.expr }.forEach(::walkExpr)
            }
            is RsAssertMacroArgument -> {
                argument.expr?.let(::walkExpr)
                argument.formatMacroArgList.map { it.expr }.forEach(::walkExpr)
            }

            is RsMacroArgument -> {
                val expansion = macroCall.expansion
                if (expansion != null) {
                    for (expandedElement in expansion.elements) {
                        walk(expandedElement)
                    }
                } else {
                    useAllPaths(macroCall)
                    useAllMacroBodyIdents(macroCall)
                }
            }

            null -> useAllMacroBodyIdents(macroCall)

            else -> error("unreachable")
        }
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
            for (binding in pat.descendantsOfType<RsPatBinding>()) {
                delegate.declarationWithoutInit(binding)
            }
        }
    }

    private fun walkCondition(condition: RsCondition) {
        val init = condition.expr
        walkExpr(init)
        val initCmt = mc.processExpr(init)
        for (pat in condition.patList) {
            walkIrrefutablePat(initCmt, pat)
        }
    }

    private fun walkBlock(block: RsBlock) {
        val (expandedStmts, tailExpr) = block.expandedStmtsAndTailExpr
        for (element in expandedStmts) {
            walk(element)
        }

        if (tailExpr != null) {
            consumeExpr(tailExpr)
        }
    }

    private fun walk(element: RsElement) {
        when (element) {
            is RsStmt -> walkStmt(element)
            is RsExpr -> walkExpr(element)
            is RsMacroCall -> walkMacroCall(element)
        }
    }

    private fun walkStructExpr(fields: List<RsStructLiteralField>, withExpr: RsExpr?) {
        for (field in fields) {
            val expr = field.expr
            if (expr != null) {
                consumeExpr(expr)
            } else if (field.identifier != null) {
                val binding = field.resolveToBinding() ?: continue
                val mutability = binding.mutability
                val type = binding.type
                val cmt = Cmt(field, Local(binding), MutabilityCategory.from(mutability), type)
                delegateConsume(field, cmt)
            }
        }

        if (withExpr == null) return

        val withCmt = mc.processExpr(withExpr)
        val withType = withCmt.ty
        if (withType is TyAdt) {
            val structFields = (withType.item as? RsStructItem)?.namedFields.orEmpty()
            for (field in structFields) {
                val isMentioned = fields.any { it.identifier?.text == field.identifier.text }
                if (!isMentioned) {
                    val interior = Interior.Field(withCmt, field.name)
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
        mc.walkPat(discriminantCmt, pat) { subPatCmt, _, binding ->
            newMode = when (binding.kind) {
                is BindByReference -> newMode.leastUpperBound(BorrowingMatch)
                is BindByValue -> newMode.leastUpperBound(copyOrMove(mc, subPatCmt, PatBindingMove).matchMode)
            }
        }

        return newMode
    }

    /**
     * The core driver for walking a pattern; [matchMode] must be established up front, e.g. via [determinePatMoveMode]
     * (see also [walkIrrefutablePat] for patterns that stand alone)
     */
    private fun walkPat(discriminantCmt: Cmt, pat: RsPat, matchMode: MatchMode) {
        mc.walkPat(discriminantCmt, pat) { subPatCmt, subPat, binding ->
            val mutabilityCategory = MutabilityCategory.from(binding.mutability)
            val bindingCmt = Cmt(binding, Local(binding), mutabilityCategory, binding.type)

            // Each match binding is effectively an assignment to the binding being produced.
            delegate.mutate(subPat, bindingCmt, MutateMode.Init)

            // It is also a borrow or copy/move of the value being matched.
            if (binding.kind is BindByValue) {
                // In case of NonConsumingMatch (e.g. `for x in xs {}`), the pat should not be consumed as copy/move,
                // but should be consumed as usage
                if (matchMode != NonConsumingMatch || delegate is GatherLivenessContext) {
                    delegate.consumePat(subPat, subPatCmt, copyOrMove(mc, subPatCmt, PatBindingMove))
                }
            }
        }
    }
}

fun copyOrMove(mc: MemoryCategorizationContext, cmt: Cmt, moveReason: MoveReason): ConsumeMode =
    if (cmt.ty.isMovesByDefault(mc.lookup)) Move(moveReason) else Copy
