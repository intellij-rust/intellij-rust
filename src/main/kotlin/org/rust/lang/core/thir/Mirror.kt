/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.mir.asSpan
import org.rust.lang.core.mir.schemas.MirBorrowKind
import org.rust.lang.core.mir.schemas.MirSpan
import org.rust.lang.core.mir.schemas.toBorrowKind
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.adjustments
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.infer.Adjustment
import org.rust.lang.core.types.regions.Scope
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyArray
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type

// TODO: contextOwner is unused now, but it will be needed for rvalue scopes in loop (and more I think)

fun RsBlock.mirrorAsExpr(contextOwner: RsInferenceContextOwner, ty: Ty, span: MirSpan = this.asSpan): ThirExpr {
    val mirrored = mirror(ty, contextOwner, span)
    return completeMirroring(mirrored, emptyList(), span)
}

fun RsExpr.mirror(contextOwner: RsInferenceContextOwner, span: MirSpan = this.asSpan): ThirExpr {
    val mirrored = mirrorUnadjusted(contextOwner, span)
    return completeMirroring(mirrored, adjustments, span)
}

// Special case because of field shorthands
fun RsStructLiteralField.mirror(contextOwner: RsInferenceContextOwner): ThirExpr {
    // `Foo { a: 0 }`
    val expr = expr
    if (expr != null) return expr.mirror(contextOwner)

    // `Foo { a }`
    val span = asSpan
    val mirrored = convert(type, span)
    return completeMirroring(mirrored, adjustments, span)
}

private fun RsElement.completeMirroring(
    mirrored: ThirExpr,
    adjustments: List<Adjustment>,
    span: MirSpan,
): ThirExpr {
    // TODO: this is just hardcoded for now
    val adjusted = if (this is RsBreakExpr) {
        applyAdjustment(this, mirrored, Adjustment.NeverToAny(TyUnit.INSTANCE))
    } else {
        adjustments.fold(mirrored) { thir, adjustment ->
            applyAdjustment(this, thir, adjustment)
        }
    }

    return ThirExpr.Scope(Scope.Node(span.reference), adjusted, adjusted.ty, span)
    // TODO:
    //  1. there are some rvalue scopes stored in thir expression
    //  2. something about destruction scope
}

private fun RsExpr.mirrorUnadjusted(contextOwner: RsInferenceContextOwner, span: MirSpan): ThirExpr {
    val ty = type
    return when (this) {
        is RsParenExpr -> expr?.mirror(contextOwner, span) ?: error("Could not get expr of paren expression")
        is RsCallExpr -> {
            val callee = expr
            // TODO Fn, FnMut, FnOnce
            // TODO tuple-struct constructor
            ThirExpr.Call(
                callee.type,
                callee.mirror(contextOwner),
                valueArgumentList.exprList.map { it.mirror(contextOwner) },
                fromCall = true,
                ty,
                span
            )
        }
        is RsUnaryExpr -> {
            when {
                this.minus != null -> {
                    val clearExpr = expr?.let { unwrapParenExprs(it) } ?: error("Could not get expr of unary operator")
                    if (clearExpr is RsLitExpr) {
                        ThirExpr.Literal(
                            literal = clearExpr,
                            neg = true,
                            ty = ty,
                            span = span,
                        )
                    } else {
                        ThirExpr.Unary(
                            op = UnaryOperator.MINUS,
                            arg = expr?.mirror(contextOwner) ?: error("Could not get expr of unary operator"),
                            ty = ty,
                            span = span,
                        )
                    }
                }
                this.excl != null -> ThirExpr.Unary(
                    op = UnaryOperator.NOT,
                    arg = expr?.mirror(contextOwner) ?: error("Could not get expr of unary operator"),
                    ty = ty,
                    span = span,
                )
                this.and != null -> ThirExpr.Borrow(
                    if (this.mut == null) MirBorrowKind.Shared else MirBorrowKind.Mut(false),
                    this.expr?.mirror(contextOwner) ?: error("Could not get expr of borrow"),
                    ty = ty,
                    span = span,
                )
                this.mul != null -> TODO() // deref
                else -> throw IllegalStateException("Unexpected unary operator")
            }
        }
        is RsLitExpr -> ThirExpr.Literal(this, false, ty, span)
        is RsBinaryExpr -> {
            when (val operator = this.binaryOp.operatorType) {
                is ArithmeticOp -> ThirExpr.Binary(
                    op = operator,
                    left = left.mirror(contextOwner),
                    right = right?.mirror(contextOwner) ?: error("Could not get rhs of arithmetic operator"),
                    ty = ty,
                    span = span,
                )
                is LogicOp -> ThirExpr.Logical(
                    op = operator,
                    left = left.mirror(contextOwner),
                    right = right?.mirror(contextOwner) ?: error("Could not get rhs of logical operator"),
                    ty = ty,
                    span = span,
                )
                is AssignmentOp.EQ -> ThirExpr.Assign(
                    left = left.mirror(contextOwner),
                    right = right?.mirror(contextOwner) ?: error("Could not get rhs of assignment"),
                    ty = ty,
                    span = span,
                )
                is ArithmeticAssignmentOp -> {
                    // TODO custom method call (not builtin)
                    ThirExpr.AssignOp(
                        op = operator.nonAssignEquivalent,
                        left = left.mirror(contextOwner),
                        right = right?.mirror(contextOwner) ?: error("Could not get rhs of assignment"),
                        ty = ty,
                        span = span,
                    )
                }
                else -> TODO()
            }
        }
        is RsBlockExpr -> ThirExpr.Block(block.mirrorBlock(contextOwner, span), ty, span)
        is RsIfExpr -> {
            val then = this.block ?: error("Can't get then block of if expr")
            ThirExpr.If(
                ifThenScope = Scope.IfThen(span.reference),
                cond = this.condition?.expr?.mirror(contextOwner) ?: error("Can't get condition of if expr"),
                then = then.mirror(type, contextOwner),
                `else` = this.elseBranch?.block?.mirror(type, contextOwner),
                ty = ty,
                span = span,
            )
        }
        is RsUnitExpr -> ThirExpr.Tuple(emptyList(), ty, span)
        is RsTupleExpr -> ThirExpr.Tuple(exprList.map { it.mirror(contextOwner) }, ty, span)
        is RsDotExpr -> {
            val fieldLookup = fieldLookup
            val methodCall = methodCall
            when {
                fieldLookup != null -> {
                    val integerLiteral = fieldLookup.integerLiteral ?: TODO("Named fields not implemented")
                    val fieldIndex = integerLiteral.text.toIntOrNull() ?: error("Invalid field integer literal")
                    ThirExpr.Field(expr.mirror(contextOwner), fieldIndex, ty, span)
                }
                methodCall != null -> TODO("Method calls not implemented")
                else -> error("Invalid dot expr")
            }
        }
        // TODO: `for`s should be also handled into ThirExpr.Loop
        is RsLoopExpr -> {
            val blockTy = TyUnit.INSTANCE // compiler forces it to be unit
            // TODO: proper rvalue scopes, it is needed to be in ThirExpr (not only in loop)
            val block = this.block?.mirrorBlock(contextOwner, span) ?: error("Could not find body of loop")
            val body = ThirExpr.Block(block, blockTy, block.source)
            ThirExpr.Loop(body, ty, span)
        }
        is RsBreakExpr -> {
            val target = label
                ?.run { reference.resolve() ?: error("Cannot resolve break target") }
                ?: run {
                    contexts.filterIsInstance<RsLooplikeExpr>().firstOrNull() ?: error("Could not find break's loop")
                }
            ThirExpr.Break(
                label = Scope.Node(target),
                expr = this.expr?.mirror(contextOwner),
                ty = ty,
                span = span,
            )
        }
        is RsPathExpr -> this.convert(ty, span)
        is RsArrayExpr -> {
            val initializer = initializer
            val sizeExpr = sizeExpr
            val arrayElements = arrayElements
            val count = (ty as? TyArray)?.const ?: CtUnknown
            when {
                arrayElements != null ->
                    ThirExpr.Array(arrayElements.map { it.mirror(contextOwner) }, ty, span)
                initializer != null && sizeExpr != null ->
                    ThirExpr.Repeat(initializer.mirror(contextOwner), count, ty, span)
                else -> error("Incomplete array expr")
            }
        }
        is RsStructLiteral -> {
            check(ty is TyAdt) { "Unexpected type for struct literal" }
            when (val item = ty.item) {
                is RsStructItem -> {
                    val body = structLiteralBody
                    val base = body.expr?.let { TODO() }
                    val fields = fieldRefs(body.structLiteralFieldList, item.fields, contextOwner)
                    ThirExpr.Adt(item, fields, base, ty, span)
                }
                is RsEnumItem -> TODO()
                else -> error("unreachable")
            }
        }
        else -> TODO("Not implemented for ${this::class}")
    }
}

private fun fieldRefs(
    fieldLiterals: List<RsStructLiteralField>,
    fieldDeclarations: List<RsFieldDecl>,
    contextOwner: RsInferenceContextOwner
): List<FieldExpr> {
    return fieldLiterals.map {
        val fieldDeclaration = it.resolveToDeclaration() ?: error("Could not resolve RsStructLiteralField")
        val fieldIndex = fieldDeclarations.indexOf(fieldDeclaration)
        if (fieldIndex == -1) error("Can't find RsFieldDecl for RsStructLiteralField")
        FieldExpr(fieldIndex, it.mirror(contextOwner))
    }
}

private fun RsPathExpr.convert(ty: Ty, source: MirSpan): ThirExpr {
    val resolved = this.path.reference?.resolve() ?: error("Could not resolve RsPathExpr")
    return convert(resolved, ty, source)
}

private fun RsStructLiteralField.convert(ty: Ty, source: MirSpan): ThirExpr {
    val resolved = resolveToBinding() ?: error("Could not resolve RsStructLiteralField")
    return convert(resolved, ty, source)
}

private fun convert(resolved: RsElement, ty: Ty, source: MirSpan): ThirExpr =
    when (resolved) {
        is RsPatBinding -> ThirExpr.VarRef(LocalVar(resolved), ty, source) // TODO: captured values are not yet handled
        is RsStructItem -> ThirExpr.Adt(resolved, fields = emptyList(), base = null, ty, source)
        is RsFunction -> ThirExpr.ZstLiteral(ty, source)
        else -> TODO()
    }

private fun RsBlock.mirror(
    ty: Ty,
    contextOwner: RsInferenceContextOwner,
    source: MirSpan = this.asSpan,
): ThirExpr {
    return ThirExpr.Block(this.mirrorBlock(contextOwner, source), ty, this.asSpan)
}

private fun RsBlock.mirrorBlock(contextOwner: RsInferenceContextOwner, source: MirSpan): ThirBlock {
    val (stmts, expr) = this.expandedStmtsAndTailExpr
    return ThirBlock(
        scope = Scope.Node(source.reference),
        statements = stmts.mirror(contextOwner, this),
        expr = expr?.mirror(contextOwner),
        source = this.asSpan
    )
}

private fun List<RsStmt>.mirror(contextOwner: RsInferenceContextOwner, block: RsBlock): List<ThirStatement> {
    return mapIndexed { index, stmt ->
        when (stmt) {
            is RsLetDecl -> {
                val remainderScope = Scope.Remainder(block, stmt)
                val elseBlock: ThirBlock? = null // TODO
                val pattern = ThirPat.from(stmt.pat ?: error("Could not find pattern"))
                // TODO: pattern can be changed if user type is provided
                ThirStatement.Let(
                    remainderScope = remainderScope,
                    initScope = Scope.Node(stmt),
                    pattern = pattern,
                    initializer = stmt.expr?.mirror(contextOwner),
                    elseBlock = elseBlock,
                )
            }
            is RsExprStmt -> {
                ThirStatement.Expr(
                    scope = Scope.Node(stmt),
                    expr = stmt.expr.mirror(contextOwner),
                )
            }
            else -> TODO()
        }
    }
}

private fun applyAdjustment(psiExpr: RsElement, thirExpr: ThirExpr, adjustment: Adjustment): ThirExpr {
    return when (adjustment) {
        is Adjustment.NeverToAny -> ThirExpr.NeverToAny(thirExpr, adjustment.target, thirExpr.span)
        is Adjustment.BorrowPointer -> TODO()
        is Adjustment.BorrowReference -> {
            // TODO See fix_scalar_builtin_expr - borrow adjustment for binary operators on scalars should be removed before MIR building
            return thirExpr

            val borrowKind = adjustment.mutability.toBorrowKind()
            ThirExpr.Borrow(borrowKind, thirExpr, adjustment.target, thirExpr.span)
        }
        is Adjustment.Deref -> TODO()
        is Adjustment.MutToConstPointer -> TODO()
        is Adjustment.Unsize -> TODO()
        is Adjustment.ClosureFnPointer -> TODO()
        is Adjustment.ReifyFnPointer -> TODO()
        is Adjustment.UnsafeFnPointer -> TODO()
    }
}
