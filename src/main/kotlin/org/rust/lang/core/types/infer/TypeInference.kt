/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import com.intellij.psi.PsiElement
import org.rust.ide.utils.isNullOrEmpty
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.StdKnownItems
import org.rust.lang.core.resolve.ref.resolveFieldLookupReferenceWithReceiverType
import org.rust.lang.core.resolve.ref.resolveMethodCallReferenceWithReceiverType
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.utils.forEachChild

fun inferTypesIn(fn: RsFunction): RsInferenceContext =
    RsInferenceContext().apply { preventRecursion { infer(fn) } }

class RsInferenceContext {
    private val bindings: MutableMap<RsPatBinding, Ty> = HashMap()
    private val exprTypes: MutableMap<RsExpr, Ty> = HashMap()

    fun infer(fn: RsFunction) {
        extractParameterBindings(fn)

        val block = fn.block
        if (block != null) {
            val items = StdKnownItems.relativeTo(fn)
            RsFnInferenceContext(this, ImplLookup(fn.project, items), items).inferBlockType(block)
        }
    }

    private fun extractParameterBindings(fn: RsFunction) {
        for (param in fn.valueParameters) {
            extractBindings(param.pat, param.typeReference?.type ?: TyUnknown)
        }
    }

    fun getExprType(expr: RsExpr): Ty {
        return exprTypes[expr] ?: TyUnknown
    }

    fun isTypeInferred(expr: RsExpr): Boolean {
        return exprTypes.containsKey(expr)
    }

    fun getBindingType(binding: RsPatBinding): Ty {
        return bindings[binding] ?: TyUnknown
    }

    fun writeTy(psi: RsExpr, ty: Ty) {
        exprTypes[psi] = ty
    }

    fun extractBindings(pattern: RsPat?, baseType: Ty) {
        if (pattern != null) bindings += collectBindings(pattern, baseType)
    }

    override fun toString(): String {
        return "RsInferenceContext(bindings=$bindings, exprTypes=$exprTypes)"
    }
}

private class RsFnInferenceContext(
    private val ctx: RsInferenceContext,
    private val lookup: ImplLookup,
    private val items: StdKnownItems
) {
    private val RsStructLiteralField.type: Ty get() = resolveToDeclaration?.typeReference?.type ?: TyUnknown

    fun inferBlockType(block: RsBlock): Ty {
        for (stmt in block.stmtList) {
            processStatement(stmt)
        }

        return block.expr?.inferType() ?: TyUnit
    }

    private fun processStatement(psi: RsStmt) {
        when (psi) {
            is RsLetDecl -> {
                val exprTy = psi.expr?.inferType() ?: TyUnknown
                ctx.extractBindings(psi.pat, psi.typeReference?.type ?: exprTy)
            }
            is RsExprStmt -> psi.expr.inferType()
        }
    }

    private fun RsExpr.inferType(): Ty {
        if (ctx.isTypeInferred(this)) error("Trying to infer expression type twice")

        val ty = when (this) {
            is RsPathExpr -> inferPathExprType(this)
            is RsStructLiteral -> inferStructLiteralType(this)
            is RsTupleExpr -> inferRsTupleExprType(this)
            is RsParenExpr -> this.expr.inferType()
            is RsUnitExpr -> TyUnit
            is RsCastExpr -> inferCastExprType(this)
            is RsCallExpr -> inferCallExprType(this)
            is RsDotExpr -> inferDotExprType(this)
            is RsLitExpr -> inferLiteralExprType(this)
            is RsBlockExpr -> inferBlockType(this.block)
            is RsIfExpr -> inferIfExprType(this)
            is RsLoopExpr -> inferLoopExprType(this)
            is RsWhileExpr -> inferWhileExprType(this)
            is RsForExpr -> inferForExprType(this)
            is RsMatchExpr -> inferMatchExprType(this)
            is RsUnaryExpr -> inferUnaryExprType(this)
            is RsBinaryExpr -> inferBinaryExprType(this)
            is RsTryExpr -> inferTryExprType(this)
            is RsArrayExpr -> inferArrayType(this)
            is RsRangeExpr -> inferRangeType(this)
            is RsIndexExpr -> inferIndexExprType(this)
            is RsMacroExpr -> inferMacroExprType(this)
            is RsLambdaExpr -> inferLambdaExprType(this)
            is RsRetExpr -> inferRetExprType(this)
            is RsBreakExpr -> inferBreakExprType(this)
            else -> TyUnknown
        }

        ctx.writeTy(this, ty)
        return ty
    }

    private fun inferPathExprType(expr: RsPathExpr): Ty {
        val (element, subst) = expr.path.reference.advancedResolve()?.downcast<RsNamedElement>() ?: return TyUnknown
        val type = if (element is RsPatBinding) ctx.getBindingType(element) else inferDeclarationType(element)
        val tupleFields = (element as? RsFieldsOwner)?.tupleFields
        return if (tupleFields != null) {
            // Treat tuple constructor as a function
            TyFunction(tupleFields.tupleFieldDeclList.map { it.typeReference.type }, type)
        } else {
            type
        }.substitute(subst)
    }

    private fun inferStructLiteralType(expr: RsStructLiteral): Ty {
        val boundElement = expr.path.reference.advancedResolve()
        if (boundElement == null) {
            inferStructTypeArguments(expr)
            return TyUnknown
        }
        val (element, subst) = boundElement
        return when (element) {
            is RsStructItem -> element.type
            is RsEnumVariant -> element.parentEnum.type
            else -> TyUnknown
        }.substitute(subst).substitute(inferStructTypeArguments(expr))
    }

    private fun inferRsTupleExprType(expr: RsTupleExpr): Ty {
        return TyTuple(expr.exprList.map { it.inferType() })
    }

    fun inferCastExprType(expr: RsCastExpr): Ty {
        expr.expr.inferType()
        return expr.typeReference.type
    }

    private fun inferCallExprType(expr: RsCallExpr): Ty {
        val ty = expr.expr.inferType()
        val argExprs = expr.valueArgumentList.exprList
        // `struct S; S();`
        if (ty is TyStructOrEnumBase && argExprs.isEmpty()) return ty

        val calleeType = lookup.asTyFunction(ty) ?: unknownTyFunction(argExprs.size)
        return calleeType.retType.substitute(mapTypeParameters(calleeType.paramTypes, argExprs))
    }

    private fun inferMethodCallExprType(methodCall: RsMethodCall): Ty {
        val receiver = methodCall.receiver.inferType()
        val argExprs = methodCall.valueArgumentList.exprList
        val boundElement = resolveMethodCallReferenceWithReceiverType(lookup, receiver, methodCall)
            .firstOrNull()?.downcast<RsFunction>()
        val methodType = boundElement?.element?.type as? TyFunction ?: unknownTyFunction(argExprs.size + 1)
        // drop first element of paramTypes because it's `self` param
        // and it doesn't have value in `methodCall.valueArgumentList.exprList`
        val inferredSubst = mapTypeParameters(methodType.paramTypes.drop(1), argExprs)

        return methodType.retType
            .substitute(boundElement?.subst ?: emptySubstitution)
            .substitute(mapOf(TyTypeParameter.self() to receiver))
            .substitute(inferredSubst)
    }

    private fun unknownTyFunction(arity: Int): TyFunction =
        TyFunction(generateSequence { TyUnknown }.take(arity).toList(), TyUnknown)

    private fun inferFieldExprType(fieldLookup: RsFieldLookup): Ty {
        val receiverTy = fieldLookup.receiver.inferType()
        val boundField = resolveFieldLookupReferenceWithReceiverType(lookup, receiverTy, fieldLookup).firstOrNull()
        if (boundField == null) {
            for (type in lookup.derefTransitively(receiverTy)) {
                if (type is TyTuple) {
                    val fieldIndex = fieldLookup.integerLiteral?.text?.toIntOrNull() ?: return TyUnknown
                    return type.types.getOrElse(fieldIndex) { TyUnknown }
                }
            }
            return TyUnknown
        }
        val field = boundField.element
        val raw = when (field) {
            is RsFieldDecl -> field.typeReference?.type
            is RsTupleFieldDecl -> field.typeReference.type
            else -> null
        } ?: TyUnknown
        return raw.substitute(boundField.subst)
    }

    private fun inferDotExprType(expr: RsDotExpr): Ty {
        val methodCall = expr.methodCall
        val fieldLookup = expr.fieldLookup
        return when {
            methodCall != null -> inferMethodCallExprType(methodCall)
            fieldLookup != null -> inferFieldExprType(fieldLookup)
            else -> TyUnknown
        }
    }

    private fun inferLoopExprType(expr: RsLoopExpr): Ty {
        expr.block?.let(this::inferBlockType)
        val returningTypes = mutableListOf<Ty>()
        val label = expr.labelDecl?.name

        fun collectReturningTypes(element: PsiElement, matchOnlyByLabel: Boolean) {
            element.forEachChild { child ->
                when (child) {
                    is RsBreakExpr -> {
                        collectReturningTypes(child, matchOnlyByLabel)
                        if (!matchOnlyByLabel && child.label == null || child.label?.referenceName == label) {
                            returningTypes += child.expr?.let(ctx::getExprType) ?: TyUnit
                        }
                    }
                    is RsLabeledExpression -> {
                        if (label != null) {
                            collectReturningTypes(child, true)
                        }
                    }
                    else -> collectReturningTypes(child, matchOnlyByLabel)
                }
            }
        }

        collectReturningTypes(expr, false)
        return getMoreCompleteType(returningTypes)
    }

    private fun inferForExprType(expr: RsForExpr): Ty {
        val exprTy = expr.expr?.inferType() ?: TyUnknown
        ctx.extractBindings(expr.pat, lookup.findIteratorItemType(exprTy))
        expr.block?.let { inferBlockType(it) }
        return TyUnit
    }

    private fun inferWhileExprType(expr: RsWhileExpr): Ty {
        expr.condition?.let { ctx.extractBindings(it.pat, it.expr.inferType()) }
        expr.block?.let(this::inferBlockType)
        return TyUnit
    }

    private fun inferMatchExprType(expr: RsMatchExpr): Ty {
        val matchingExprTy = expr.expr?.inferType() ?: TyUnknown
        val arms = expr.matchBody?.matchArmList.orEmpty()
        for (arm in arms) {
            for (pat in arm.patList) {
                ctx.extractBindings(pat, matchingExprTy)
            }
            arm.expr?.inferType()
        }

        return arms.asSequence()
            .mapNotNull { it.expr?.let(ctx::getExprType) }
            .firstOrNull { it !is TyUnknown }
            ?: TyUnknown
    }

    private fun inferUnaryExprType(expr: RsUnaryExpr): Ty {
        val base = expr.expr?.inferType() ?: return TyUnknown
        return when (expr.operatorType) {
            UnaryOperator.REF -> TyReference(base, mutable = false)
            UnaryOperator.REF_MUT -> TyReference(base, mutable = true)
            UnaryOperator.DEREF -> when (base) {
                is TyReference -> base.referenced
                is TyPointer -> base.referenced
                else -> TyUnknown
            }
            UnaryOperator.MINUS -> base
            UnaryOperator.NOT -> TyBool
            UnaryOperator.BOX -> TyUnknown
        }
    }

    private fun inferIfExprType(expr: RsIfExpr): Ty {
        expr.condition?.let { ctx.extractBindings(it.pat, it.expr.inferType()) }
        val blockTy = expr.block?.let(this::inferBlockType)
        val elseBranch = expr.elseBranch
        if (elseBranch != null) {
            elseBranch.ifExpr?.inferType()
            elseBranch.block?.let(this::inferBlockType)
        }
        return if (expr.elseBranch == null) TyUnit else (blockTy ?: TyUnknown)
    }

    private fun inferBinaryExprType(expr: RsBinaryExpr): Ty {
        val lhsType = expr.left.inferType()
        val rhsType = expr.right?.inferType() ?: TyUnknown
        val op = expr.operatorType
        return when (op) {
            is BoolOp -> TyBool
            is ArithmeticOp -> inferArithmeticBinaryExprType(op, lhsType, rhsType)
            is AssignmentOp -> TyUnit
        }
    }

    private fun inferTryExprType(expr: RsTryExpr): Ty {
        // See RsMacroExpr where we handle the try! macro in a similar way
        val base = expr.expr.inferType()

        return if (base is TyEnum && base.item == items.findResultItem())
            base.typeArguments.firstOrNull() ?: TyUnknown
        else
            TyUnknown
    }

    private fun inferRangeType(expr: RsRangeExpr): Ty {
        val el = expr.exprList
        val dot2 = expr.dotdot
        val dot3 = expr.dotdotdot

        val (rangeName, indexType) = when {
            dot2 != null && el.size == 0 -> "RangeFull" to null
            dot2 != null && el.size == 1 -> {
                val e = el[0]
                if (e.startOffsetInParent < dot2.startOffsetInParent) {
                    "RangeFrom" to e.inferType()
                } else {
                    "RangeTo" to e.inferType()
                }
            }
            dot2 != null && el.size == 2 -> {
                "Range" to getMoreCompleteType(el[0].inferType(), el[1].inferType())
            }
            dot3 != null && el.size == 1 -> {
                val e = el[0]
                if (e.startOffsetInParent < dot3.startOffsetInParent) {
                    return TyUnknown
                } else {
                    "RangeToInclusive" to e.inferType()
                }
            }
            dot3 != null && el.size == 2 -> {
                "RangeInclusive" to getMoreCompleteType(el[0].inferType(), el[1].inferType())
            }

            else -> error("Unrecognized range expression")
        }

        return items.findRangeTy(rangeName, indexType)
    }

    private fun inferIndexExprType(expr: RsIndexExpr): Ty {
        val containerType = expr.containerExpr?.inferType() ?: return TyUnknown
        val indexType = expr.indexExpr?.inferType() ?: return TyUnknown
        return lookup.findIndexOutputType(containerType, indexType)
    }

    private fun inferMacroExprType(expr: RsMacroExpr): Ty {
        inferChildExprsRecursively(expr.macroCall)
        val vecArg = expr.macroCall.vecMacroArgument
        if (vecArg != null) {
            val elementTypes = vecArg.exprList.map { ctx.getExprType(it) }
            val elementType = getMoreCompleteType(elementTypes)
            return items.findVecForElementTy(elementType)
        }

        val tryArg = expr.macroCall.tryMacroArgument
        if (tryArg != null) {
            // See RsTryExpr where we handle the ? expression in a similar way
            val base = ctx.getExprType(tryArg.expr)
            return if (base is TyEnum && base.item == items.findResultItem())
                base.typeArguments.firstOrNull() ?: TyUnknown
            else
                TyUnknown
        }

        val name = expr.macroCall.macroName?.text ?: return TyUnknown
        return when {
            "print" in name || "assert" in name -> TyUnit
            name == "format" -> items.findStringTy()
            name == "format_args" -> items.findArgumentsTy()
            expr.macroCall.formatMacroArgument != null || expr.macroCall.logMacroArgument != null -> TyUnit
            else -> TyUnknown
        }
    }

    private fun inferChildExprsRecursively(psi: PsiElement) {
        when (psi) {
            is RsExpr -> psi.inferType()
            else -> psi.forEachChild(this::inferChildExprsRecursively)
        }
    }

    private fun inferLambdaExprType(expr: RsLambdaExpr): Ty {
        val params = expr.valueParameterList.valueParameterList
        for (p in params) {
            p.typeReference?.type?.let { ctx.extractBindings(p.pat, it) }
        }
        val ty = deviseLambdaType(expr)
        if (ty is TyFunction) {
            params.zip(ty.paramTypes).forEach { (p, t) -> ctx.extractBindings(p.pat, t) }
        }
        val exprTy = expr.expr?.inferType()
        return if (ty is TyFunction) TyFunction(ty.paramTypes, exprTy ?: ty.retType) else TyUnknown
    }

    private fun deviseLambdaType(lambdaExpr: RsLambdaExpr): Ty {
        val fallback = TyFunction(emptyList(), TyUnknown)
        val parent = lambdaExpr.parent as? RsValueArgumentList ?: return fallback
        val callExpr = parent.parent
        val containingFunctionType = when (callExpr) {
            is RsCallExpr -> ctx.getExprType(callExpr.expr)
            is RsMethodCall -> {
                val fn = resolveMethodCallReferenceWithReceiverType(lookup, ctx.getExprType(callExpr.receiver), callExpr)
                    .firstOrNull()
                    ?.downcast<RsFunction>()
                    ?: return fallback
                fn.element.type.substitute(fn.subst)
            }
            else -> null
        } as? TyFunction
            ?: return fallback

        val lambdaArgumentPosition = parent.exprList.indexOf(lambdaExpr) + (if (callExpr is RsMethodCall) 1 else 0)

        val param = containingFunctionType.paramTypes.getOrNull(lambdaArgumentPosition)
            ?: return fallback

        return lookup.asTyFunction(param)?.substitute(containingFunctionType.typeParameterValues) ?: fallback
    }

    private fun inferArrayType(expr: RsArrayExpr): Ty {
        val (elementType, size) = if (expr.semicolon != null) {
            val elementType = expr.initializer?.inferType() ?: return TySlice(TyUnknown)
            expr.sizeExpr?.inferType()
            val size = calculateArraySize(expr.sizeExpr) ?: return TySlice(elementType)
            elementType to size
        } else {
            val elementTypes = expr.arrayElements?.map { it.inferType() }
            if (elementTypes.isNullOrEmpty()) return TySlice(TyUnknown)

            // '!!' is safe here because we've just checked that elementTypes isn't null
            val elementType = getMoreCompleteType(elementTypes!!)
            elementType to elementTypes.size
        }
        return TyArray(elementType, size)
    }

    private fun inferRetExprType(expr: RsRetExpr): Ty {
        expr.expr?.inferType()
        return TyUnit // TODO TyNever `!`
    }

    private fun inferBreakExprType(expr: RsBreakExpr): Ty {
        expr.expr?.inferType()
        return TyUnit // TODO TyNever `!`
    }

    private fun getMoreCompleteType(types: List<Ty>): Ty {
        if (types.isEmpty()) return TyUnknown
        return types.reduce { acc, ty -> getMoreCompleteType(acc, ty) }
    }

    private fun inferStructTypeArguments(literal: RsStructLiteral): Substitution =
        inferFieldTypeArguments(literal.structLiteralBody.structLiteralFieldList)

    private fun inferFieldTypeArguments(fieldExprs: List<RsStructLiteralField>): Substitution =
        UnifyResult.mergeAll(
            fieldExprs.mapNotNull { field -> field.expr?.let { expr -> field.type.unifyWith(expr.inferType(), lookup) } }
        ).substitution().orEmpty()

    private fun mapTypeParameters(argDefs: List<Ty>, argExprs: List<RsExpr>): Substitution {
        // extending argument definitions to be sure that type inference launched for each arg expr
        val argDefsExt = argDefs.asSequence() + generateSequence { TyUnknown }
        val results = argDefsExt.zip(argExprs.asSequence()).map { (type, expr) ->
            type.unifyWith(expr.inferType(), lookup)
        }
        val subst = UnifyResult.mergeAll(results.asIterable()).substitution().orEmpty()
        return subst.substituteInValues(subst) // TODO multiple times?
    }

    private fun inferArithmeticBinaryExprType(op: ArithmeticOp, lhsType: Ty, rhsType: Ty): Ty {
        return lookup.findArithmeticBinaryExprOutputType(lhsType, rhsType, op)
    }
}

private val threadLocalGuard: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

/**
 * This function asserts that code inside a lambda don't call itself recursively.
 */
private inline fun preventRecursion(action: () -> Unit) {
    if (threadLocalGuard.get()) error("Can not run nested type inference")
    threadLocalGuard.set(true)
    try {
        action()
    } finally {
        threadLocalGuard.set(false)
    }
}
