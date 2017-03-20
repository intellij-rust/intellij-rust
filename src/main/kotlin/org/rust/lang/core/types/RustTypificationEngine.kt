package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.types.*

object RustTypificationEngine {
    fun typifyExpr(expr: RsExpr): RustType =
        RustExprTypificationVisitor().compute(expr)

    fun typify(named: RsNamedElement): RustType {
        return when (named) {
            is RsStructItem -> RustStructType(named)

            is RsEnumItem -> RustEnumType(named)
            is RsEnumVariant -> RustEnumType((named.parent as RsEnumBody).parent as RsEnumItem)

            is RsTypeAlias -> named.typeReference?.type ?: RustUnknownType

            is RsFunction -> deviseFunctionType(named)

            is RsTraitItem -> RustTraitType(named)

            is RsConstant -> named.typeReference?.type ?: RustUnknownType

            is RsSelfParameter -> deviseSelfType(named)

            is RsPatBinding -> inferPatternBindingType(named)

            is RsTypeParameter -> RustTypeParameterType(named)

            else -> RustUnknownType
        }
    }
}

private class RustExprTypificationVisitor : RustComputingVisitor<RustType>() {

    override fun visitExpr(o: RsExpr) = set {
        // Default until we handle all the cases explicitly
        RustUnknownType
    }


    override fun visitCastExpr(o: RsCastExpr) = set { o.typeReference.type }

    override fun visitUnaryExpr(o: RsUnaryExpr) = set {
        if (o.box != null)
            RustUnknownType
        else {
            val base = o.expr?.type
                ?: return@set RustUnknownType
            when {
                (o.and != null) -> RustReferenceType(base, o.mut != null)
                (o.mul != null && base is RustReferenceType) -> base.referenced
                (o.mul != null && base is RustPointerType) -> base.referenced
                else -> base
            }
        }
    }

    override fun visitPathExpr(o: RsPathExpr) = set {
        val resolve = o.path.reference.resolve() as? RsNamedElement
        resolve?.let { RustTypificationEngine.typify(it) } ?: RustUnknownType
    }

    override fun visitStructExpr(o: RsStructExpr) = set {
        val base = o.path.reference.resolve()
        when (base) {
            is RsStructItem -> base.type
            is RsEnumVariant -> base.parentEnum.type
            else -> RustUnknownType
        }
    }

    override fun visitTupleExpr(o: RsTupleExpr) = set {
        RustTupleType(o.exprList.map { RustTypificationEngine.typifyExpr(it) })
    }

    override fun visitUnitExpr(o: RsUnitExpr) = set {
        RustUnitType
    }

    override fun visitCallExpr(o: RsCallExpr) = set {
        val fn = o.expr
        if (fn is RsPathExpr) {
            val variant = fn.path.reference.resolve()
            when (variant) {
                is RsEnumVariant -> return@set variant.parentEnum.type
                is RsStructItem -> return@set variant.type
            }
        }

        val calleeType = fn.type
        (calleeType as? RustFunctionType)?.retType ?: RustUnknownType
    }

    override fun visitMethodCallExpr(o: RsMethodCallExpr) = set {
        val method = o.reference.resolve() as? RsFunction
            ?: return@set RustUnknownType

        val impl = method.parentOfType<RsImplItem>()
        val typeParameterMap = impl?.remapTypeParameters(o.expr.type.typeParameterValues).orEmpty()

        deviseFunctionType(method).retType.substitute(typeParameterMap)
    }

    override fun visitFieldExpr(o: RsFieldExpr) = set {
        val field = o.reference.resolve()
        val raw = when (field) {
            is RsFieldDecl -> field.typeReference?.type
            is RsTupleFieldDecl -> field.typeReference.type
            else -> null
        } ?: RustUnknownType
        raw.substitute(o.expr.type.typeParameterValues)
    }

    override fun visitLitExpr(o: RsLitExpr) = set {
        when (o.kind) {
            is RsLiteralKind.Boolean -> RustBooleanType
            is RsLiteralKind.Integer -> RustIntegerType.fromLiteral(o.integerLiteral!!)
            is RsLiteralKind.Float -> RustFloatType.fromLiteral(o.floatLiteral!!)
            is RsLiteralKind.String -> RustReferenceType(RustStringSliceType)
            is RsLiteralKind.Char -> RustCharacterType
            null -> RustUnknownType
        }
    }

    override fun visitBlockExpr(o: RsBlockExpr) = set {
        o.block.type
    }

    override fun visitIfExpr(o: RsIfExpr) = set {
        if (o.elseBranch == null)
            RustUnitType
        else
            o.block?.type ?: RustUnknownType
    }

    override fun visitMatchExpr(o: RsMatchExpr) = set {
        o.matchBody?.matchArmList.orEmpty().asSequence()
            .mapNotNull { it.expr?.type }
            .firstOrNull { it !is RustUnknownType }
            ?: RustUnknownType
    }

    override fun visitWhileExpr(o: RsWhileExpr) = set { RustUnitType }
    override fun visitLoopExpr(o: RsLoopExpr) = set { RustUnitType }
    override fun visitForExpr(o: RsForExpr) = set { RustUnitType }

    override fun visitParenExpr(o: RsParenExpr) = set { o.expr.type }

    override fun visitBinaryExpr(o: RsBinaryExpr) = set {
        when (o.operatorType) {
            ANDAND,
            OROR,
            EQEQ,
            EXCLEQ,
            LT,
            GT,
            GTEQ,
            LTEQ -> RustBooleanType

            else -> RustUnknownType
        }
    }

    override fun visitTryExpr(o: RsTryExpr) = set {
        val base = o.expr.type
        // This is super hackish. Need to figure out how to
        // identify known types (See also the CString inspection).
        // Java uses fully qualified names for this, perhaps we
        // can do this as well? Will be harder to test though :(
        if (base is RustEnumType && base.item.name == "Result")
            base.typeArguments.firstOrNull() ?: RustUnknownType
        else
            RustUnknownType
    }

    private val RsBlock.type: RustType get() = expr?.type ?: RustUnitType
}


/**
 * Devises type for the given (implicit) self-argument
 */
private fun deviseSelfType(self: RsSelfParameter): RustType {
    val impl = self.parentOfType<RsImplItem>()
    var Self: RustType = if (impl != null) {
        impl.typeReference?.type ?: return RustUnknownType
    } else {
        val trait = self.parentOfType<RsTraitItem>()
            ?: return RustUnknownType
        RustTypeParameterType(trait)
    }

    if (self.isRef) {
        Self = RustReferenceType(Self, mutable = self.isMut)
    }

    return Self
}

private fun deviseFunctionType(fn: RsFunction): RustFunctionType {
    val paramTypes = mutableListOf<RustType>()

    val self = fn.selfParameter
    if (self != null) {
        paramTypes += deviseSelfType(self)
    }

    paramTypes += fn.valueParameters.map { it.typeReference?.type ?: RustUnknownType }

    return RustFunctionType(paramTypes, fn.retType?.typeReference?.type ?: RustUnitType)
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
private fun RsImplItem.remapTypeParameters(
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
