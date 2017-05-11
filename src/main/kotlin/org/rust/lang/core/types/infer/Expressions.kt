package org.rust.lang.core.types.infer

import org.rust.ide.utils.isNullOrEmpty
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.Ty
import org.rust.lang.core.types.type
import org.rust.lang.core.types.types.*

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

        is RsTupleExpr -> RustTupleType(expr.exprList.map { it.type })
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

            val calleeType = fn.type as? RustFunctionType ?: return TyUnknown
            calleeType.retType.substitute(mapTypeParameters(calleeType.paramTypes, expr.valueArgumentList.exprList))
        }

        is RsMethodCallExpr -> {
            val method = expr.reference.resolve() as? RsFunction
                ?: return TyUnknown

            val impl = method.parentOfType<RsImplItem>()
            val typeParameterMap = impl?.remapTypeParameters(expr.expr.type.typeParameterValues).orEmpty()
            return (method.retType?.typeReference?.type ?: TyUnit).substitute(typeParameterMap)
        }

        is RsFieldExpr -> {
            val field = expr.reference.resolve()
            val raw = when (field) {
                is RsFieldDecl -> field.typeReference?.type
                is RsTupleFieldDecl -> field.typeReference.type
                else -> null
            } ?: TyUnknown
            raw.substitute(expr.expr.type.typeParameterValues)
        }

        is RsLitExpr -> {
            when (expr.kind) {
                is RsLiteralKind.Boolean -> TyBool
                is RsLiteralKind.Integer -> TyInteger.fromLiteral(expr.integerLiteral!!)
                is RsLiteralKind.Float -> TyFloat.fromLiteral(expr.floatLiteral!!)
                is RsLiteralKind.String -> RustReferenceType(TyStr)
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
                UnaryOperator.REF -> RustReferenceType(base, mutable = false)
                UnaryOperator.REF_MUT -> RustReferenceType(base, mutable = true)
                UnaryOperator.DEREF -> when (base) {
                    is RustReferenceType -> base.referenced
                    is RustPointerType -> base.referenced
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
            // identify known types (See also the CString inspection).
            // Java uses fully qualified names for this, perhaps we
            // can do this as well? Will be harder to test though :(
            if (base is RustEnumType && base.item.name == "Result")
                base.typeArguments.firstOrNull() ?: TyUnknown
            else
                TyUnknown
        }

        is RsArrayExpr -> inferArrayType(expr)

        else -> TyUnknown
    }
}

private fun inferArrayType(expr: RsArrayExpr): Ty {
    val (elementType, size) = if (expr.semicolon != null) {
        val elementType = expr.initializer?.type ?: return RustSliceType(TyUnknown)
        val size = calculateArraySize(expr.sizeExpr) ?: return RustSliceType(elementType)
        elementType to size
    } else {
        val elements = expr.arrayElements
        if (elements.isNullOrEmpty()) return RustSliceType(TyUnknown)
        // '!!' is safe here because we've just checked that elements isn't null
        elements!![0].type to elements.size
    }
    return RustArrayType(elementType, size)
}

private val RsBlock.type: Ty get() = expr?.type ?: TyUnit

private fun inferStructTypeParameters(o: RsStructLiteral, item: RsStructItem): Ty {
    val baseType = item.type
    if ((baseType as? RustStructOrEnumTypeBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.blockFields?.let { inferTypeParametersForFields(o.structLiteralBody.structLiteralFieldList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferEnumTypeParameters(o: RsStructLiteral, item: RsEnumVariant): Ty {
    val baseType = item.parentEnum.type
    if ((baseType as? RustStructOrEnumTypeBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.blockFields?.let { inferTypeParametersForFields(o.structLiteralBody.structLiteralFieldList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferTupleStructTypeParameters(o: RsCallExpr, item: RsStructItem): Ty {
    val baseType = item.type
    if ((baseType as? RustStructOrEnumTypeBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.tupleFields?.let { inferTypeParametersForTuple(o.valueArgumentList.exprList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferTupleEnumTypeParameters(o: RsCallExpr, item: RsEnumVariant): Ty {
    val baseType = item.parentEnum.type
    if ((baseType as? RustStructOrEnumTypeBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.tupleFields?.let { inferTypeParametersForTuple(o.valueArgumentList.exprList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferTypeParametersForFields(
    structLiteralFieldList: List<RsStructLiteralField>,
    fields: RsBlockFields
): Map<RustTypeParameterType, Ty> {
    val argsMapping = mutableMapOf<RustTypeParameterType, Ty>()
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
): Map<RustTypeParameterType, Ty> {
    return mapTypeParameters(tupleFields.tupleFieldDeclList.map { it.typeReference.type }, tupleExprs)
}

private fun mapTypeParameters(
    argDefs: Iterable<Ty>,
    argExprs: Iterable<RsExpr>
): Map<RustTypeParameterType, Ty> {
    val argsMapping = mutableMapOf<RustTypeParameterType, Ty>()
    argExprs.zip(argDefs).forEach { (expr, type) -> addTypeMapping(argsMapping, type, expr) }
    return argsMapping
}

private fun addTypeMapping(
    argsMapping: MutableMap<RustTypeParameterType, Ty>,
    fieldType: Ty?,
    expr: RsExpr
) {
    if (fieldType is RustTypeParameterType) {
        val old = argsMapping[fieldType]
        if (old == null || old == TyUnknown || old is TyNumeric && old.isKindWeak)
            argsMapping[fieldType] = expr.type
    }
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
fun RsImplItem.remapTypeParameters(
    map: Map<RustTypeParameterType, Ty>
): Map<RustTypeParameterType, Ty> =
    typeReference?.type?.typeParameterValues.orEmpty()
        .mapNotNull {
            val (structParam, structType) = it
            if (structType is RustTypeParameterType) {
                val implType = map[structParam] ?: return@mapNotNull null
                structType to implType
            } else {
                null
            }
        }.toMap()
