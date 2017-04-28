package org.rust.lang.core.types.infer

import org.rust.ide.utils.isNullOrEmpty
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.type
import org.rust.lang.core.types.types.*

fun inferExpressionType(expr: RsExpr): RustType {
    return when (expr) {
        is RsPathExpr -> {
            val target = expr.path.reference.resolve() as? RsNamedElement
                ?: return RustUnknownType

            inferDeclarationType(target)
        }

        is RsStructLiteral -> {
            val base = expr.path.reference.resolve()
            when (base) {
                is RsStructItem -> inferStructTypeParameters(expr, base)
                is RsEnumVariant -> inferEnumTypeParameters(expr, base)
                else -> RustUnknownType
            }
        }

        is RsTupleExpr -> RustTupleType(expr.exprList.map { it.type })
        is RsParenExpr -> expr.expr.type
        is RsUnitExpr -> RustUnitType
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

            val calleeType = fn.type as? RustFunctionType ?: return RustUnknownType
            calleeType.retType.substitute(mapTypeParameters(calleeType.paramTypes, expr.valueArgumentList.exprList))
        }

        is RsMethodCallExpr -> {
            val method = expr.reference.resolve() as? RsFunction
                ?: return RustUnknownType

            val impl = method.parentOfType<RsImplItem>()
            val typeParameterMap = impl?.remapTypeParameters(expr.expr.type.typeParameterValues).orEmpty()
            return (method.retType?.typeReference?.type ?: RustUnitType).substitute(typeParameterMap)
        }

        is RsFieldExpr -> {
            val field = expr.reference.resolve()
            val raw = when (field) {
                is RsFieldDecl -> field.typeReference?.type
                is RsTupleFieldDecl -> field.typeReference.type
                else -> null
            } ?: RustUnknownType
            raw.substitute(expr.expr.type.typeParameterValues)
        }

        is RsLitExpr -> {
            when (expr.kind) {
                is RsLiteralKind.Boolean -> RustBooleanType
                is RsLiteralKind.Integer -> RustIntegerType.fromLiteral(expr.integerLiteral!!)
                is RsLiteralKind.Float -> RustFloatType.fromLiteral(expr.floatLiteral!!)
                is RsLiteralKind.String -> RustReferenceType(RustStringSliceType)
                is RsLiteralKind.Char -> RustCharacterType
                null -> RustUnknownType
            }
        }

        is RsBlockExpr -> expr.block.type
        is RsIfExpr -> if (expr.elseBranch == null) RustUnitType else (expr.block?.type ?: RustUnknownType)
    // TODO: handle break with value
        is RsWhileExpr, is RsLoopExpr, is RsForExpr -> return RustUnitType

        is RsMatchExpr -> {
            expr.matchBody?.matchArmList.orEmpty().asSequence()
                .mapNotNull { it.expr?.type }
                .firstOrNull { it !is RustUnknownType }
                ?: RustUnknownType
        }

        is RsUnaryExpr -> {
            val base = expr.expr?.type ?: return RustUnknownType
            return when (expr.operatorType) {
                UnaryOperator.REF -> RustReferenceType(base, mutable = false)
                UnaryOperator.REF_MUT -> RustReferenceType(base, mutable = true)
                UnaryOperator.DEREF -> when (base) {
                    is RustReferenceType -> base.referenced
                    is RustPointerType -> base.referenced
                    else -> RustUnknownType
                }
                UnaryOperator.MINUS -> base
                UnaryOperator.NOT -> RustBooleanType
                UnaryOperator.BOX -> RustUnknownType
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
            RsElementTypes.LTEQ -> RustBooleanType

            else -> RustUnknownType
        }

        is RsTryExpr -> {
            val base = expr.expr.type
            // This is super hackish. Need to figure out how to
            // identify known types (See also the CString inspection).
            // Java uses fully qualified names for this, perhaps we
            // can do this as well? Will be harder to test though :(
            if (base is RustEnumType && base.item.name == "Result")
                base.typeArguments.firstOrNull() ?: RustUnknownType
            else
                RustUnknownType
        }

        else -> RustUnknownType
    }
}


private val RsBlock.type: RustType get() = expr?.type ?: RustUnitType

private fun inferStructTypeParameters(o: RsStructLiteral, item: RsStructItem): RustType {
    val baseType = item.type
    if ((baseType as? RustStructOrEnumTypeBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.blockFields?.let { inferTypeParametersForFields(o.structLiteralBody.structLiteralFieldList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferEnumTypeParameters(o: RsStructLiteral, item: RsEnumVariant): RustType {
    val baseType = item.parentEnum.type
    if ((baseType as? RustStructOrEnumTypeBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.blockFields?.let { inferTypeParametersForFields(o.structLiteralBody.structLiteralFieldList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferTupleStructTypeParameters(o: RsCallExpr, item: RsStructItem): RustType {
    val baseType = item.type
    if ((baseType as? RustStructOrEnumTypeBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.tupleFields?.let { inferTypeParametersForTuple(o.valueArgumentList.exprList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferTupleEnumTypeParameters(o: RsCallExpr, item: RsEnumVariant): RustType {
    val baseType = item.parentEnum.type
    if ((baseType as? RustStructOrEnumTypeBase)?.typeArguments.isNullOrEmpty()) return baseType
    val argsMapping = item.tupleFields?.let { inferTypeParametersForTuple(o.valueArgumentList.exprList, it) } ?: emptyMap()
    return if (argsMapping.isEmpty()) baseType else baseType.substitute(argsMapping)
}

private fun inferTypeParametersForFields(
    structLiteralFieldList: List<RsStructLiteralField>,
    fields: RsBlockFields
): Map<RustTypeParameterType, RustType> {
    val argsMapping = mutableMapOf<RustTypeParameterType, RustType>()
    val fieldTypes = fields.fieldDeclList
        .associate { it.identifier.text to (it.typeReference?.type ?: RustUnknownType) }
    structLiteralFieldList.forEach { field ->
        field.expr?.let { expr -> addTypeMapping(argsMapping, fieldTypes[field.identifier.text], expr) }
    }
    return argsMapping
}

private fun inferTypeParametersForTuple(
    tupleExprs: List<RsExpr>,
    tupleFields: RsTupleFields
): Map<RustTypeParameterType, RustType> {
    return mapTypeParameters(tupleFields.tupleFieldDeclList.map { it.typeReference.type }, tupleExprs)
}

private fun mapTypeParameters(
    argDefs: Iterable<RustType>,
    argExprs: Iterable<RsExpr>
): Map<RustTypeParameterType, RustType> {
    val argsMapping = mutableMapOf<RustTypeParameterType, RustType>()
    argExprs.zip(argDefs).forEach { (expr, type) -> addTypeMapping(argsMapping, type, expr) }
    return argsMapping
}

private fun addTypeMapping(
    argsMapping: MutableMap<RustTypeParameterType, RustType>,
    fieldType: RustType?,
    expr: RsExpr
) {
    if (fieldType is RustTypeParameterType) {
        val old = argsMapping[fieldType]
        if (old == null || old == RustUnknownType || old is RustNumericType && old.isKindWeak)
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
    map: Map<RustTypeParameterType, RustType>
): Map<RustTypeParameterType, RustType> =
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
