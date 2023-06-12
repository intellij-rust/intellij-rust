/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.mir.asSpan
import org.rust.lang.core.mir.schemas.MirBorrowKind
import org.rust.lang.core.mir.schemas.MirSpan
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

// TODO: [contextOwner] is unused now, but it will be needed for rvalue scopes in loop (and more I think)
class MirrorContext(private val contextOwner: RsInferenceContextOwner) {

    fun mirrorBlock(block: RsBlock, ty: Ty, span: MirSpan = block.asSpan): ThirExpr {
        val mirrored = mirror(block, ty, span)
        return completeMirroring(block, mirrored, emptyList(), span)
    }

    fun mirrorExpr(expr: RsExpr, span: MirSpan = expr.asSpan): ThirExpr {
        val mirrored = mirrorUnadjusted(expr, span)
        return completeMirroring(expr, mirrored, expr.adjustments, span)
    }

    // Special case because of field shorthands
    private fun mirror(field: RsStructLiteralField): ThirExpr {
        // `Foo { a: 0 }`
        val expr = field.expr
        if (expr != null) return mirrorExpr(expr)

        // `Foo { a }`
        val span = field.asSpan
        val mirrored = convert(field, field.type, span)
        return completeMirroring(field, mirrored, field.adjustments, span)
    }

    private fun completeMirroring(
        element: RsElement,
        mirrored: ThirExpr,
        adjustments: List<Adjustment>,
        span: MirSpan
    ): ThirExpr {
        // TODO: this is just hardcoded for now
        val adjusted = if (element is RsBreakExpr) {
            applyAdjustment(element, mirrored, Adjustment.NeverToAny(TyUnit.INSTANCE))
        } else {
            adjustments.fold(mirrored) { thir, adjustment ->
                applyAdjustment(element, thir, adjustment)
            }
        }

        return ThirExpr.Scope(Scope.Node(span.reference), adjusted, adjusted.ty, span)
        // TODO:
        //  1. there are some rvalue scopes stored in thir expression
        //  2. something about destruction scope
    }

    private fun mirrorUnadjusted(expr: RsExpr, span: MirSpan): ThirExpr {
        val ty = expr.type
        return when (expr) {
            is RsParenExpr -> expr.expr?.let { mirrorExpr(it, span) } ?: error("Could not get expr of paren expression")
            is RsCallExpr -> {
                val callee = expr.expr
                // TODO Fn, FnMut, FnOnce
                // TODO tuple-struct constructor
                ThirExpr.Call(
                    callee.type,
                    mirrorExpr(callee),
                    expr.valueArgumentList.exprList.map { mirrorExpr(it) },
                    fromCall = true,
                    ty,
                    span
                )
            }

            is RsUnaryExpr -> {
                when {
                    expr.minus != null -> {
                        val clearExpr = expr.expr?.let { unwrapParenExprs(it) }
                            ?: error("Could not get expr of unary operator")
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
                                arg = expr.expr?.let { mirrorExpr(it) } ?: error("Could not get expr of unary operator"),
                                ty = ty,
                                span = span,
                            )
                        }
                    }

                    expr.excl != null -> ThirExpr.Unary(
                        op = UnaryOperator.NOT,
                        arg = expr.expr?.let { mirrorExpr(it) } ?: error("Could not get expr of unary operator"),
                        ty = ty,
                        span = span,
                    )

                    expr.and != null -> ThirExpr.Borrow(
                        kind = if (expr.mut == null) MirBorrowKind.Shared else MirBorrowKind.Mut(false),
                        arg = expr.expr?.let { mirrorExpr(it) } ?: error("Could not get expr of borrow"),
                        ty = ty,
                        span = span,
                    )

                    expr.mul != null -> ThirExpr.Deref(
                        arg = expr.expr?.let { mirrorExpr(it) } ?: error("Could not get expr of dereg"),
                        ty = ty,
                        span = span,
                    )

                    else -> throw IllegalStateException("Unexpected unary operator")
                }
            }

            is RsLitExpr -> ThirExpr.Literal(expr, false, ty, span)
            is RsBinaryExpr -> {
                when (val operator = expr.binaryOp.operatorType) {
                    is ArithmeticOp -> ThirExpr.Binary(
                        op = operator,
                        left = mirrorExpr(expr.left),
                        right = expr.right?.let { mirrorExpr(it) } ?: error("Could not get rhs of arithmetic operator"),
                        ty = ty,
                        span = span
                    )

                    is LogicOp -> ThirExpr.Logical(
                        op = operator,
                        left = mirrorExpr(expr.left),
                        right = expr.right?.let { mirrorExpr(it) } ?: error("Could not get rhs of logical operator"),
                        ty = ty,
                        span = span
                    )

                    is AssignmentOp.EQ -> ThirExpr.Assign(
                        left = mirrorExpr(expr.left),
                        right = expr.right?.let { mirrorExpr(it) } ?: error("Could not get rhs of assignment"),
                        ty = ty,
                        span = span
                    )

                    is ArithmeticAssignmentOp -> {
                        // TODO custom method call (not builtin)
                        ThirExpr.AssignOp(
                            op = operator.nonAssignEquivalent,
                            left = mirrorExpr(expr.left),
                            right = expr.right?.let { mirrorExpr(it) } ?: error("Could not get rhs of assignment"),
                            ty = ty,
                            span = span
                        )
                    }

                    else -> TODO()
                }
            }

            is RsBlockExpr -> ThirExpr.Block(mirrorBlock(expr.block, span), ty, span)
            is RsIfExpr -> {
                ThirExpr.If(
                    ifThenScope = Scope.IfThen(span.reference),
                    cond = expr.condition?.expr?.let { mirrorExpr(it) } ?: error("Can't get condition of if expr"),
                    then = expr.block?.let { mirror(it, ty) } ?: error("Can't get then block of if expr"),
                    `else` = expr.elseBranch?.block?.let { mirror(it, ty) },
                    ty = ty,
                    span = span
                )
            }

            is RsUnitExpr -> ThirExpr.Tuple(emptyList(), ty, span)
            is RsTupleExpr -> ThirExpr.Tuple(expr.exprList.map { mirrorExpr(it) }, ty, span)
            is RsDotExpr -> {
                val fieldLookup = expr.fieldLookup
                val methodCall = expr.methodCall
                when {
                    fieldLookup != null -> {
                        val integerLiteral = fieldLookup.integerLiteral ?: TODO("Named fields not implemented")
                        val fieldIndex = integerLiteral.text.toIntOrNull() ?: error("Invalid field integer literal")
                        ThirExpr.Field(mirrorExpr(expr.expr), fieldIndex, ty, span)
                    }

                    methodCall != null -> TODO("Method calls not implemented")
                    else -> error("Invalid dot expr")
                }
            }
            // TODO: `for`s should be also handled into ThirExpr.Loop
            is RsLoopExpr -> {
                val blockTy = TyUnit.INSTANCE // compiler forces it to be unit
                // TODO: proper rvalue scopes, it is needed to be in ThirExpr (not only in loop)
                val block = expr.block?.let { mirrorBlock(it, span) } ?: error("Could not find body of loop")
                val body = ThirExpr.Block(block, blockTy, block.source)
                ThirExpr.Loop(body, ty, span)
            }

            is RsBreakExpr -> {
                val target = expr.label
                    ?.run { reference.resolve() ?: error("Cannot resolve break target") }
                    ?: run {
                        expr.contexts.filterIsInstance<RsLooplikeExpr>().firstOrNull()
                            ?: error("Could not find break's loop")
                    }
                ThirExpr.Break(
                    label = Scope.Node(target),
                    expr = expr.expr?.let { mirrorExpr(it) },
                    ty = ty,
                    span = span
                )
            }

            is RsPathExpr -> convert(expr, ty, span)
            is RsArrayExpr -> {
                val initializer = expr.initializer
                val sizeExpr = expr.sizeExpr
                val arrayElements = expr.arrayElements
                val count = (ty as? TyArray)?.const ?: CtUnknown
                when {
                    arrayElements != null ->
                        ThirExpr.Array(arrayElements.map { mirrorExpr(it) }, ty, span)

                    initializer != null && sizeExpr != null ->
                        ThirExpr.Repeat(mirrorExpr(initializer), count, ty, span)

                    else -> error("Incomplete array expr")
                }
            }

            is RsStructLiteral -> {
                check(ty is TyAdt) { "Unexpected type for struct literal" }
                val body = expr.structLiteralBody
                val base = body.expr?.let { TODO() }
                when (val item = ty.item) {
                    is RsStructItem -> {
                        val fields = fieldRefs(body.structLiteralFieldList, item.namedFields)
                        ThirExpr.Adt(item, variantIndex = 0, fields, base, ty, span)
                    }
                    is RsEnumItem -> {
                        val variant = expr.path.reference?.resolve() as? RsEnumVariant ?: error("Unexpected resolve result")
                        val fields = fieldRefs(body.structLiteralFieldList, variant.namedFields)
                        val variantIndex = item.indexOfVariant(variant) ?: error("Can't find enum variant")
                        ThirExpr.Adt(item, variantIndex, fields, base, ty, span)
                    }
                    else -> error("unreachable")
                }
            }

            is RsIndexExpr -> {
                // TODO: support overloaded index
                ThirExpr.Index(
                    mirrorExpr(expr.containerExpr),
                    expr.indexExpr?.let { mirrorExpr(it) } ?: error("Could not get index"),
                    ty,
                    span
                )
            }

            else -> TODO("Not implemented for ${expr::class}")
        }
    }

    private fun fieldRefs(
        fieldLiterals: List<RsStructLiteralField>,
        fieldDeclarations: List<RsFieldDecl>
    ): List<FieldExpr> = fieldLiterals.map {
        val fieldDeclaration = it.resolveToDeclaration() ?: error("Could not resolve RsStructLiteralField")
        val fieldIndex = fieldDeclarations.indexOf(fieldDeclaration)
        if (fieldIndex == -1) error("Can't find RsFieldDecl for RsStructLiteralField")
        FieldExpr(fieldIndex, mirror(it))
    }

    private fun convert(path: RsPathExpr, ty: Ty, source: MirSpan): ThirExpr {
        val resolved = path.path.reference?.resolve() ?: error("Could not resolve RsPathExpr")
        return convert(resolved, ty, source)
    }

    private fun convert(field: RsStructLiteralField, ty: Ty, source: MirSpan): ThirExpr {
        val resolved = field.resolveToBinding() ?: error("Could not resolve RsStructLiteralField")
        return convert(resolved, ty, source)
    }

    private fun convert(resolved: RsElement, ty: Ty, source: MirSpan): ThirExpr =
        when (resolved) {
            is RsPatBinding -> ThirExpr.VarRef(LocalVar(resolved), ty, source) // TODO: captured values are not yet handled
            is RsFieldsOwner -> {
                check(resolved.isFieldless)
                val (definition, variantIndex) = when (resolved) {
                    is RsStructItem -> resolved to 0
                    is RsEnumVariant -> {
                        val enum = resolved.parentEnum
                        val variantIndex = enum.indexOfVariant(resolved) ?: error("Can't find enum variant")
                        enum to variantIndex
                    }
                    else -> error("unreachable")
                }
                ThirExpr.Adt(definition, variantIndex, fields = emptyList(), base = null, ty, source)
            }
            is RsFunction -> ThirExpr.ZstLiteral(ty, source)
            else -> TODO()
        }

    private fun mirror(block: RsBlock, ty: Ty, source: MirSpan = block.asSpan): ThirExpr =
        ThirExpr.Block(mirrorBlock(block, source), ty, block.asSpan)

    private fun mirrorBlock(block: RsBlock, source: MirSpan): ThirBlock {
        val (stmts, expr) = block.expandedStmtsAndTailExpr
        return ThirBlock(
            scope = Scope.Node(source.reference),
            statements = mirror(stmts, block),
            expr = expr?.let { mirrorExpr(it) },
            source = block.asSpan
        )
    }

    private fun mirror(statements: List<RsStmt>, block: RsBlock): List<ThirStatement> =
        statements.map { stmt ->
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
                        initializer = stmt.expr?.let { mirrorExpr(it) },
                        elseBlock = elseBlock,
                    )
                }

                is RsExprStmt -> {
                    ThirStatement.Expr(
                        scope = Scope.Node(stmt),
                        expr = mirrorExpr(stmt.expr),
                    )
                }

                else -> TODO()
            }
        }

    private fun applyAdjustment(psiExpr: RsElement, thirExpr: ThirExpr, adjustment: Adjustment): ThirExpr =
        when (adjustment) {
            is Adjustment.NeverToAny -> ThirExpr.NeverToAny(thirExpr, adjustment.target, thirExpr.span)
            is Adjustment.BorrowPointer -> TODO()
            is Adjustment.BorrowReference -> {
                // TODO: See fix_scalar_builtin_expr - borrow adjustment for binary operators on scalars should be removed before MIR building
                thirExpr

                // val borrowKind = adjustment.mutability.toBorrowKind()
                // ThirExpr.Borrow(borrowKind, thirExpr, adjustment.target, thirExpr.span)
            }
            is Adjustment.Deref -> TODO()
            is Adjustment.MutToConstPointer -> TODO()
            is Adjustment.Unsize -> TODO()
            is Adjustment.ClosureFnPointer -> TODO()
            is Adjustment.ReifyFnPointer -> TODO()
            is Adjustment.UnsafeFnPointer -> TODO()
        }
}

private fun RsEnumItem.indexOfVariant(variant: RsEnumVariant): Int? =
    variants.indexOf(variant).takeIf { it != -1 }

fun RsStructOrEnumItemElement.variant(index: MirVariantIndex): RsFieldsOwner =
    when (this) {
        is RsStructItem -> this
        is RsEnumItem -> variants[index]
        else -> error("unreachable")
    }
