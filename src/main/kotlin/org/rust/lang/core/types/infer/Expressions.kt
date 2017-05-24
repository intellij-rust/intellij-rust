package org.rust.lang.core.types.infer

import org.rust.ide.utils.isNullOrEmpty
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.findIndexOutputType
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

fun inferExpressionType(expr: RsExpr): Ty {
    return when (expr) {
        is RsPathExpr -> {
            val target = expr.path.reference.resolve() as? RsNamedElement
                ?: return TyUnknown

            inferDeclarationType(target)
        }

        is RsStructLiteral -> {
            val base = expr.path.reference.resolve()
            when (base) {
                is RsStructItem -> inferStructTypeParameters(expr, base)
                is RsEnumVariant -> inferEnumTypeParameters(expr, base)
                else -> TyUnknown
            }
        }

        is RsTupleExpr -> TyTuple(expr.exprList.map { it.type })
        is RsParenExpr -> expr.expr.type
        is RsUnitExpr -> TyUnit
        is RsCastExpr -> expr.typeReference.type

        is RsCallExpr -> {
            val fn = expr.expr
            if (fn is RsPathExpr) {
                val variant = fn.path.reference.resolve()
                when (variant) {
                    is RsEnumVariant -> return inferTupleEnumTypeParameters(expr, variant)
                    is RsStructItem -> return inferTupleStructTypeParameters(expr, variant)
                }
            }

            val calleeType = fn.type as? TyFunction ?: return TyUnknown
            calleeType.retType.substitute(mapTypeParameters(calleeType.paramTypes, expr.valueArgumentList.exprList))
        }

        is RsMethodCallExpr -> {
            val boundMethod = expr.reference.advancedResolve()
            val method = boundMethod?.element as? RsFunction ?: return TyUnknown
            return (method.retType?.typeReference?.type ?: TyUnit).substitute(boundMethod.typeArguments)
        }

        is RsFieldExpr -> {
            val boundField = expr.reference.advancedResolve()
            if (boundField == null) {
                val type = expr.expr.type as? TyTuple ?: return TyUnknown
                val fieldIndex = expr.fieldId.integerLiteral?.text?.toInt() ?: return TyUnknown
                return type.types.getOrElse(fieldIndex) { TyUnknown }
            }
            val field = boundField.element
            val raw = when (field) {
                is RsFieldDecl -> field.typeReference?.type
                is RsTupleFieldDecl -> field.typeReference.type
                else -> null
            } ?: TyUnknown
            raw.substitute(boundField.typeArguments)
        }

        is RsLitExpr -> {
            when (expr.kind) {
                is RsLiteralKind.Boolean -> TyBool
                is RsLiteralKind.Integer -> TyInteger.fromLiteral(expr.integerLiteral!!)
                is RsLiteralKind.Float -> TyFloat.fromLiteral(expr.floatLiteral!!)
                is RsLiteralKind.String -> TyReference(TyStr)
                is RsLiteralKind.Char -> TyChar
                null -> TyUnknown
            }
        }

        is RsBlockExpr -> expr.block.type
        is RsIfExpr -> if (expr.elseBranch == null) TyUnit else (expr.block?.type ?: TyUnknown)
    // TODO: handle break with value
        is RsWhileExpr, is RsLoopExpr, is RsForExpr -> return TyUnit

        is RsMatchExpr -> {
            expr.matchBody?.matchArmList.orEmpty().asSequence()
                .mapNotNull { it.expr?.type }
                .firstOrNull { it !is TyUnknown }
                ?: TyUnknown
        }

        is RsUnaryExpr -> {
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

        is RsBinaryExpr -> when (expr.operatorType) {
            RsElementTypes.ANDAND,
            RsElementTypes.OROR,
            RsElementTypes.EQEQ,
            RsElementTypes.EXCLEQ,
            RsElementTypes.LT,
            RsElementTypes.GT,
            RsElementTypes.GTEQ,
            RsElementTypes.LTEQ -> TyBool

            else -> TyUnknown
        }

        is RsTryExpr -> {
            val base = expr.expr.type
            // This is super hackish. Need to figure out how to
            // identify known ty (See also the CString inspection).
            // Java uses fully qualified names for this, perhaps we
            // can do this as well? Will be harder to test though :(
            if (base is TyEnum && base.item.name == "Result")
                base.typeArguments.firstOrNull() ?: TyUnknown
            else
                TyUnknown
        }

        is RsArrayExpr -> inferArrayType(expr)

        is RsIndexExpr -> {
            val containerType = expr.containerExpr?.type ?: return TyUnknown
            val indexType = expr.indexExpr?.type ?: return TyUnknown
            findIndexOutputType(expr.project, containerType, indexType)
        }

        else -> TyUnknown
    }
}

private fun inferArrayType(expr: RsArrayExpr): Ty {
    val (elementType, size) = if (expr.semicolon != null) {
        val elementType = expr.initializer?.type ?: return TySlice(TyUnknown)
        val size = calculateArraySize(expr.sizeExpr) ?: return TySlice(elementType)
        elementType to size
    } else {
        val elements = expr.arrayElements
        if (elements.isNullOrEmpty()) return TySlice(TyUnknown)
        // '!!' is safe here because we've just checked that elements isn't null
        elements!![0].type to elements.size
    }
    return TyArray(elementType, size)
}

private val RsBlock.type: Ty get() = expr?.type ?: TyUnit

private fun inferStructTypeParameters(o: RsStructLiteral, item: RsStructItem): Ty {
    val baseType = item.type
    if ((baseType as? TyStructOrEnumBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.blockFields?.let { inferTypeParametersForFields(o.structLiteralBody.structLiteralFieldList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferEnumTypeParameters(o: RsStructLiteral, item: RsEnumVariant): Ty {
    val baseType = item.parentEnum.type
    if ((baseType as? TyStructOrEnumBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.blockFields?.let { inferTypeParametersForFields(o.structLiteralBody.structLiteralFieldList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferTupleStructTypeParameters(o: RsCallExpr, item: RsStructItem): Ty {
    val baseType = item.type
    if ((baseType as? TyStructOrEnumBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.tupleFields?.let { inferTypeParametersForTuple(o.valueArgumentList.exprList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferTupleEnumTypeParameters(o: RsCallExpr, item: RsEnumVariant): Ty {
    val baseType = item.parentEnum.type
    if ((baseType as? TyStructOrEnumBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.tupleFields?.let { inferTypeParametersForTuple(o.valueArgumentList.exprList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferTypeParametersForFields(
    structLiteralFieldList: List<RsStructLiteralField>,
    fields: RsBlockFields
): Map<TyTypeParameter, Ty> {
    val argsMapping = mutableMapOf<TyTypeParameter, Ty>()
    val fieldTypes = fields.fieldDeclList
        .associate { it.identifier.text to (it.typeReference?.type ?: TyUnknown) }
    structLiteralFieldList.forEach { field ->
        field.expr?.let { expr -> addTypeMapping(argsMapping, fieldTypes[field.identifier.text], expr) }
    }
    return argsMapping
}

private fun inferTypeParametersForTuple(
    tupleExprs: List<RsExpr>,
    tupleFields: RsTupleFields
): Map<TyTypeParameter, Ty> {
    return mapTypeParameters(tupleFields.tupleFieldDeclList.map { it.typeReference.type }, tupleExprs)
}

private fun mapTypeParameters(
    argDefs: Iterable<Ty>,
    argExprs: Iterable<RsExpr>
): Map<TyTypeParameter, Ty> {
    val argsMapping = mutableMapOf<TyTypeParameter, Ty>()
    argExprs.zip(argDefs).forEach { (expr, type) -> addTypeMapping(argsMapping, type, expr) }
    return argsMapping
}

private fun addTypeMapping(argsMapping: TypeMapping, fieldType: Ty?, expr: RsExpr) =
    fieldType?.canUnifyWith(expr.type, expr.project, argsMapping)

/**
 * Remap type parameters between type declaration and an impl block.
 *
 * Think about the following example:
 * ```
 * struct Flip<A, B> { ... }
 * impl<X, Y> Flip<Y, X> { ... }
 * ```
 */
fun RsImplItem.remapTypeParameters(
    map: Map<TyTypeParameter, Ty>
): Map<TyTypeParameter, Ty> =
    typeReference?.type?.typeParameterValues.orEmpty()
        .mapNotNull {
            val (structParam, structType) = it
            if (structType is TyTypeParameter) {
                val implType = map[structParam] ?: return@mapNotNull null
                structType to implType
            } else {
                null
            }
        }.toMap()
