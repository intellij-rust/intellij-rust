/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.ide.utils.isNullOrEmpty
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.utils.toIntOrNull

fun inferExpressionType(expr: RsExpr) = when (expr) {
    is RsPathExpr -> inferPathExprType(expr)
    is RsStructLiteral -> inferStructLiteralType(expr)
    is RsTupleExpr -> TyTuple(expr.exprList.map { it.type })
    is RsParenExpr -> expr.expr.type
    is RsUnitExpr -> TyUnit
    is RsCastExpr -> expr.typeReference.type
    is RsCallExpr -> inferCallExprType(expr)
    is RsMethodCallExpr -> inferMethodCallExprType(expr)
    is RsFieldExpr -> inferFieldExprType(expr)
    is RsLitExpr -> inferLiteralExprType(expr)
    is RsBlockExpr -> expr.block.type
    is RsIfExpr -> if (expr.elseBranch == null) TyUnit else (expr.block?.type ?: TyUnknown)
    // TODO: handle break with value
    is RsWhileExpr, is RsLoopExpr, is RsForExpr -> TyUnit
    is RsMatchExpr -> inferMatchExprType(expr)
    is RsUnaryExpr -> inferUnaryExprType(expr)
    is RsBinaryExpr -> inferBinaryExprType(expr)
    is RsTryExpr -> inferTryExprType(expr)
    is RsArrayExpr -> inferArrayType(expr)
    is RsRangeExpr -> inferRangeType(expr)
    is RsIndexExpr -> inferIndexExprType(expr)
    is RsMacroExpr -> inferMacroExprType(expr)
    is RsLambdaExpr -> inferTypeForLambdaExpr(expr)
    else -> TyUnknown
}

private fun inferPathExprType(expr: RsPathExpr): Ty {
    val (element, subst) = expr.path.reference.advancedResolve()?.downcast<RsNamedElement>() ?: return TyUnknown
    val type = inferDeclarationType(element)
    return ((element as? RsFieldsOwner)?.tupleFields?.let {
        TyFunction(it.tupleFieldDeclList.map { it.typeReference.type }, type)
    } ?: type).substitute(subst)
}

private fun inferStructLiteralType(expr: RsStructLiteral): Ty {
    val (element, subst) = expr.path.reference.advancedResolve() ?: return TyUnknown
    return when (element) {
        is RsStructItem -> element.type
            .substitute(subst)
            .substitute(inferStructTypeArguments(expr))
        is RsEnumVariant -> element.parentEnum.type
            .substitute(subst)
            .substitute(inferStructTypeArguments(expr))
        else -> TyUnknown
    }
}

private fun inferCallExprType(expr: RsCallExpr): Ty {
    val fn = expr.expr
    val ty = fn.type
    // `struct S; S();`
    if (ty is TyStructOrEnumBase && expr.valueArgumentList.exprList.isEmpty()) return ty

    val calleeType = ty as? TyFunction ?:
        (findImplsAndTraits(fn.project, fn.type)
            .mapNotNull { it.downcast<RsTraitItem>()?.asFunctionType }
            .firstOrNull() ?: return TyUnknown)
    return calleeType.retType.substitute(mapTypeParameters(calleeType.paramTypes, expr.valueArgumentList.exprList))
}

private fun inferMethodCallExprType(expr: RsMethodCallExpr): Ty {
    val (method, subst) = expr.reference.advancedResolve()?.downcast<RsFunction>() ?: return TyUnknown

    val returnType = (method.retType?.typeReference?.type ?: TyUnit)
        .substitute(subst)
    val methodType = method.type as? TyFunction ?: return returnType
    // drop first element of paramTypes because it's `self` param
    // and it doesn't have value in `expr.valueArgumentList.exprList`
    return returnType.substitute(mapTypeParameters(methodType.paramTypes.drop(1), expr.valueArgumentList.exprList))
}

private fun inferFieldExprType(expr: RsFieldExpr): Ty {
    val boundField = expr.reference.advancedResolve()
    if (boundField == null) {
        val type = expr.expr.type as? TyTuple ?: return TyUnknown
        val fieldIndex = expr.fieldId.integerLiteral?.text?.toIntOrNull() ?: return TyUnknown
        return type.types.getOrElse(fieldIndex) { TyUnknown }
    }
    val field = boundField.element
    val raw = when (field) {
        is RsFieldDecl -> field.typeReference?.type
        is RsTupleFieldDecl -> field.typeReference.type
        else -> null
    } ?: TyUnknown
    return raw.substitute(boundField.subst)
}

private fun inferLiteralExprType(expr: RsLitExpr): Ty = when (expr.kind) {
    is RsLiteralKind.Boolean -> TyBool
    is RsLiteralKind.Integer -> TyInteger.fromLiteral(expr.integerLiteral!!)
    is RsLiteralKind.Float -> TyFloat.fromLiteral(expr.floatLiteral!!)
    is RsLiteralKind.String -> TyReference(TyStr)
    is RsLiteralKind.Char -> TyChar
    null -> TyUnknown
}

private fun inferMatchExprType(expr: RsMatchExpr): Ty =
    expr.matchBody?.matchArmList.orEmpty().asSequence()
        .mapNotNull { it.expr?.type }
        .firstOrNull { it !is TyUnknown }
        ?: TyUnknown

private fun inferUnaryExprType(expr: RsUnaryExpr): Ty {
    val base = expr.expr?.type ?: return TyUnknown
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

private fun inferBinaryExprType(expr: RsBinaryExpr): Ty {
    val op = expr.operatorType
    return when (op) {
        is BoolOp -> TyBool
        is ArithmeticOp -> inferArithmeticBinaryExprType(expr, op)
        is AssignmentOp -> TyUnit
    }
}

private fun inferTryExprType(expr: RsTryExpr): Ty {
    // See RsMacroExpr where we handle the try! macro in a similar way
    val base = expr.expr.type

    return if (isStdResult(base))
        (base as TyEnum).typeArguments.firstOrNull() ?: TyUnknown
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
                "RangeFrom" to e.type
            } else {
                "RangeTo" to e.type
            }
        }
        dot2 != null && el.size == 2 -> {
            "Range" to getMoreCompleteType(el[0].type, el[1].type)
        }
        dot3 != null && el.size == 1 -> {
            val e = el[0]
            if (e.startOffsetInParent < dot3.startOffsetInParent) {
                return TyUnknown
            } else {
                "RangeToInclusive" to e.type
            }
        }
        dot3 != null && el.size == 2 -> {
            "RangeInclusive" to getMoreCompleteType(el[0].type, el[1].type)
        }

        else -> error("Unrecognized range expression")
    }

    return findStdRange(rangeName, indexType, expr)
}

private fun inferIndexExprType(expr: RsIndexExpr): Ty {
    val containerType = expr.containerExpr?.type ?: return TyUnknown
    val indexType = expr.indexExpr?.type ?: return TyUnknown
    return findIndexOutputType(expr.project, containerType, indexType)
}

private fun inferMacroExprType(expr: RsMacroExpr): Ty {
    val vecArg = expr.macroCall.vecMacroArgument
    if (vecArg != null) {
        val elements = vecArg.exprList
        var elementType: Ty = TyUnknown
        for (e in elements) {
            elementType = getMoreCompleteType(e.type, elementType)
        }

        return findStdVec(elementType, expr)
    }

    val tryArg = expr.macroCall.tryMacroArgument
    if (tryArg != null) {
        // See RsTryExpr where we handle the ? expression in a similar way
        val base = tryArg.expr.type
        return if (isStdResult(base))
            (base as TyEnum).typeArguments.firstOrNull() ?: TyUnknown
        else
            TyUnknown
    }

    val name = expr.macroCall.macroName?.text ?: return TyUnknown
    return when {
        "print" in name || "assert" in name -> TyUnit
        name == "format" -> findStdString(expr)
        name == "format_args" -> findStdArguments(expr)
        expr.macroCall.formatMacroArgument != null || expr.macroCall.logMacroArgument != null -> TyUnit
        else -> TyUnknown
    }
}

private fun inferTypeForLambdaExpr(lambdaExpr: RsLambdaExpr): Ty {
    val fallback = TyFunction(emptyList(), TyUnknown)
    val parent = lambdaExpr.parent as? RsValueArgumentList ?: return fallback
    val callExpr = parent.parent
    val containingFunctionType = when (callExpr) {
        is RsCallExpr -> callExpr.expr.type
        is RsMethodCallExpr -> {
            val fn = callExpr.reference.advancedResolve()?.downcast<RsFunction>()
                ?: return fallback
            fn.element.type.substitute(fn.subst)
        }
        else -> null
    } as? TyFunction
        ?: return fallback

    val lambdaArgumentPosition = parent.exprList.indexOf(lambdaExpr) + (if (callExpr is RsMethodCallExpr) 1 else 0)

    val typeParameter = containingFunctionType.paramTypes.getOrNull(lambdaArgumentPosition) as? TyTypeParameter
        ?: return fallback

    val fnTrait = typeParameter.getTraitBoundsTransitively()
        .find { it.element.isAnyFnTrait }
        ?: return fallback

    return fnTrait.asFunctionType?.substitute(containingFunctionType.typeParameterValues)
        ?: fallback
}

private fun inferArrayType(expr: RsArrayExpr): Ty {
    val (elementType, size) = if (expr.semicolon != null) {
        val elementType = expr.initializer?.type ?: return TySlice(TyUnknown)
        val size = calculateArraySize(expr.sizeExpr) ?: return TySlice(elementType)
        elementType to size
    } else {
        val elements = expr.arrayElements
        if (elements.isNullOrEmpty()) return TySlice(TyUnknown)

        var elementType: Ty = TyUnknown
        // '!!' is safe here because we've just checked that elements isn't null
        for (e in elements!!) {
            elementType = getMoreCompleteType(e.type, elementType)
        }
        elementType to elements.size
    }
    return TyArray(elementType, size)
}

private fun getMoreCompleteType(t1: Ty, t2: Ty): Ty {
    return when {
        t1 is TyUnknown -> t2
        t1 is TyInteger && t2 is TyInteger && t1.isKindWeak -> t2
        t1 is TyFloat && t2 is TyFloat && t1.isKindWeak -> t2
        else -> t1
    }
}

private val RsBlock.type: Ty get() = expr?.type ?: TyUnit
private val RsStructLiteralField.type: Ty get() = resolveToDeclaration?.typeReference?.type ?: TyUnknown

private fun inferStructTypeArguments(literal: RsStructLiteral): Substitution =
    inferFieldTypeArguments(literal.structLiteralBody.structLiteralFieldList)

private fun inferFieldTypeArguments(fieldExprs: List<RsStructLiteralField>): Substitution {
    val argsMapping = mutableMapOf<TyTypeParameter, Ty>()
    fieldExprs.forEach { field -> field.expr?.let { expr -> addTypeMapping(argsMapping, field.type, expr) } }
    return argsMapping
}

private fun inferTupleStructTypeArguments(expr: RsCallExpr, item: RsFieldsOwner): Substitution =
    item.tupleFields?.let { mapTupleTypeArguments(expr.valueArgumentList.exprList, it) } ?: emptyMap()

private fun mapTupleTypeArguments(tupleExprs: List<RsExpr>, tupleFields: RsTupleFields): Substitution =
    mapTypeParameters(tupleFields.tupleFieldDeclList.map { it.typeReference.type }, tupleExprs)

private fun mapTypeParameters(argDefs: Iterable<Ty>, argExprs: Iterable<RsExpr>): Substitution {
    val argsMapping = mutableMapOf<TyTypeParameter, Ty>()
    argExprs.zip(argDefs).forEach { (expr, type) -> addTypeMapping(argsMapping, type, expr) }
    return argsMapping
}

private fun addTypeMapping(argsMapping: TypeMapping, fieldType: Ty?, expr: RsExpr) =
    fieldType?.canUnifyWith(expr.type, expr.project, argsMapping)

private fun inferArithmeticBinaryExprType(expr: RsBinaryExpr, op: ArithmeticOp): Ty {
    val lhsType = expr.left.type
    val rhsType = expr.right?.type ?: TyUnknown
    return findArithmeticBinaryExprOutputType(expr.project, lhsType, rhsType, op)
}

/**
 * Remap type parameters between type declaration and an impl block.
 *
 * Think about the following example:
 * ```
 * struct Flip<A, B> { ... }
 * impl<X, Y> Flip<Y, X> { ... }
 * ```
 */
fun RsImplItem.remapTypeParameters(subst: Substitution): Substitution {
    val positional = typeReference?.type?.typeParameterValues.orEmpty()
        .mapNotNull { (structParam, structType) ->
            if (structType is TyTypeParameter) {
                val implType = subst[structParam] ?: return@mapNotNull null
                structType to implType
            } else {
                null
            }
        }.toMap()

    val associated = (implementedTrait?.subst ?: emptyMap())
        .substituteInValues(positional)
    return positional + associated
}
