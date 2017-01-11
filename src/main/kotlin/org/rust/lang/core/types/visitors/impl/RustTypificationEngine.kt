package org.rust.lang.core.types.visitors.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.asRustPath
import org.rust.lang.core.psi.impl.mixin.parentEnum
import org.rust.lang.core.psi.impl.mixin.selfParameter
import org.rust.lang.core.psi.impl.mixin.valueParameters
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.visitors.RustComputingVisitor
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.RustUnresolvedPathType
import org.rust.lang.core.types.unresolved.RustUnresolvedReferenceType
import org.rust.lang.core.types.unresolved.RustUnresolvedTupleType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.util.type

object RustTypificationEngine {

    fun typifyType(type: RustTypeElement): RustUnresolvedType =
        RustTypeTypificationVisitor().compute(type)

    fun typifyExpr(expr: RustExprElement): RustType =
        RustExprTypificationVisitor().compute(expr)

    fun typifyItem(item: RustItemElement): RustType =
        RustItemTypificationVisitor().compute(item)

    fun typify(named: RustNamedElement): RustType {
        return when (named) {
            is RustItemElement -> typifyItem(named)

            is RustSelfParameterElement -> deviseSelfType(named)

            is RustPatBindingElement -> deviseBoundPatType(named)

            is RustEnumVariantElement -> deviseEnumType(named)

            is RustFunctionElement -> deviseFunctionType(named)

            is RustTypeParameterElement -> RustTypeParameterType(named)

            else -> RustUnknownType
        }
    }
}

private class RustExprTypificationVisitor : RustComputingVisitor<RustType>() {

    override fun visitExpr(o: RustExprElement) = set {
        // Default until we handle all the cases explicitly
        RustUnknownType
    }

    override fun visitUnaryExpr(o: RustUnaryExprElement) = set {
        if (o.box != null)
            RustUnknownType
        else
            o.expr?.resolvedType?.let {
                if (o.and != null) RustReferenceType(it, o.mut != null) else it
            } ?: RustUnknownType
    }

    override fun visitPathExpr(o: RustPathExprElement) = set {
        val resolve = o.path.reference.resolve() as? RustNamedElement
        resolve?.let { RustTypificationEngine.typify(it) } ?: RustUnknownType
    }

    override fun visitStructExpr(o: RustStructExprElement) = set {
        val base = o.path.reference.resolve()
        when (base) {
            is RustStructItemElement -> base.resolvedType
            is RustEnumVariantElement -> base.parentEnum.resolvedType
            else -> RustUnknownType
        }
    }

    override fun visitTupleExpr(o: RustTupleExprElement) = set {
        RustTupleType(o.exprList.map { RustTypificationEngine.typifyExpr(it) })
    }

    override fun visitUnitExpr(o: RustUnitExprElement) = set {
        RustUnitType
    }

    override fun visitCallExpr(o: RustCallExprElement) = set {
        val fn = o.expr
        if (fn is RustPathExprElement) {
            val variant = fn.path.reference.resolve()
            if (variant is RustEnumVariantElement) {
                return@set variant.parentEnum.resolvedType
            }
        }

        val calleeType = fn.resolvedType
        (calleeType as? RustFunctionType)?.retType ?: RustUnknownType
    }

    override fun visitMethodCallExpr(o: RustMethodCallExprElement) = set {
        val method = o.reference.resolve() as? RustFunctionElement
            ?: return@set RustUnknownType

        val impl = method.parentOfType<RustImplItemElement>()
        val typeParameterMap = impl?.remapTypeParameters(o.expr.resolvedType.typeParameterValues).orEmpty()

        deviseFunctionType(method).retType.substitute(typeParameterMap)
    }

    override fun visitFieldExpr(o: RustFieldExprElement) = set {
        val field = o.reference.resolve()
        val raw = when (field) {
            is RustFieldDeclElement -> field.type?.resolvedType
            is RustTupleFieldDeclElement -> field.type.resolvedType
            else -> null
        } ?: RustUnknownType
        raw.substitute(o.expr.resolvedType.typeParameterValues)
    }

    override fun visitLitExpr(o: RustLitExprElement) = set {
        when {
            o.integerLiteral != null -> RustIntegerType.fromLiteral(o.integerLiteral!!)
            o.floatLiteral != null -> RustFloatType.fromLiteral(o.floatLiteral!!)
            o.stringLiteral != null -> RustStringSliceType
            o.charLiteral != null -> RustCharacterType

            o.`true` != null || o.`false` != null -> RustBooleanType

            else -> RustUnknownType
        }
    }

    override fun visitBlockExpr(o: RustBlockExprElement) = set {
        o.block?.resolvedType ?: RustUnknownType
    }

    override fun visitIfExpr(o: RustIfExprElement) = set {
        if (o.elseBranch == null)
            RustUnitType
        else
            o.block?.resolvedType ?: RustUnknownType
    }

    override fun visitWhileExpr(o: RustWhileExprElement) = set { RustUnitType }
    override fun visitLoopExpr(o: RustLoopExprElement) = set { RustUnitType }
    override fun visitForExpr(o: RustForExprElement) = set { RustUnitType }

    override fun visitParenExpr(o: RustParenExprElement) = set { o.expr.resolvedType }

    override fun visitBinaryExpr(o: RustBinaryExprElement) = set {
        when (o.operatorType) {
            RustTokenElementTypes.ANDAND,
            RustTokenElementTypes.OROR,
            RustTokenElementTypes.EQEQ,
            RustTokenElementTypes.EXCLEQ,
            RustTokenElementTypes.LT,
            RustTokenElementTypes.GT,
            RustTokenElementTypes.GTEQ,
            RustTokenElementTypes.LTEQ -> RustBooleanType

            else -> RustUnknownType
        }
    }

    private val RustBlockElement.resolvedType: RustType get() = expr?.resolvedType ?: RustUnitType
}

private class RustItemTypificationVisitor : RustComputingVisitor<RustType>() {

    override fun visitElement(element: PsiElement) = set {
        check(element is RustItemElement) {
            "Panic! Should not be used with anything except the inheritors of `RustItemElement` hierarchy!"
        }

        RustUnknownType
    }

    override fun visitStructItem(o: RustStructItemElement) = set {
        RustStructType(o)
    }

    override fun visitEnumItem(o: RustEnumItemElement) = set {
        RustEnumType(o)
    }

    override fun visitTypeAlias(o: RustTypeAliasElement) = set {
        o.type?.resolvedType ?: RustUnknownType
    }

    override fun visitFunction(o: RustFunctionElement) = set {
        deviseFunctionType(o)
    }

    override fun visitTraitItem(o: RustTraitItemElement) = set {
        RustTraitType(o)
    }
}

private class RustTypeTypificationVisitor : RustComputingVisitor<RustUnresolvedType>() {

    override fun visitType(o: RustTypeElement) = set {
        RustUnknownType
    }

    override fun visitTupleType(o: RustTupleTypeElement) = set {
        // Perhaps introduce tuple_type to PSI?
        if (o.typeList.size > 0)
            RustUnresolvedTupleType(o.typeList.map { it.type })
        else
            RustUnitType
    }

    override fun visitBaseType(o: RustBaseTypeElement) = set {
        val path = o.path?.asRustPath ?: return@set RustUnknownType
        if (path is RustPath.Named && path.segments.isEmpty()) {
            val primitiveType = RustPrimitiveTypeBase.fromTypeName(path.head.name)
            if (primitiveType != null) return@set primitiveType
        }
        RustUnresolvedPathType(path)
    }

    override fun visitRefLikeType(o: RustRefLikeTypeElement) = set {
        if (o.and == null) return@set RustUnknownType //FIXME: handle pointer types
        val base = o.type ?: return@set RustUnknownType
        RustUnresolvedReferenceType(base.type, o.mut != null)
    }
}

/**
 * NOTA BENE: That's far from complete
 */
private fun deviseBoundPatType(binding: RustPatBindingElement): RustType {
    //TODO: probably want something more precise than `getTopmostParentOfType` here
    val pattern = PsiTreeUtil.getTopmostParentOfType(binding, RustPatElement::class.java) ?: return RustUnknownType
    val parent = pattern.parent
    val type = when (parent) {
        is RustLetDeclElement ->
            // use type ascription, if present or fallback to the type of the initializer expression
            parent.type?.resolvedType ?: parent.expr?.resolvedType

        is RustValueParameterElement -> parent.type?.resolvedType
        is RustConditionElement -> parent.expr.resolvedType
        else -> null
    } ?: return RustUnknownType

    return RustTypeInferenceEngine.inferPatBindingTypeFrom(binding, pattern, type)
}

/**
 * Devises type for the given (implicit) self-argument
 */
private fun deviseSelfType(self: RustSelfParameterElement): RustType {
    val impl = self.parentOfType<RustImplItemElement>()
    var Self: RustType = if (impl != null) {
        impl.type?.resolvedType ?: return RustUnknownType
    } else {
        val trait = self.parentOfType<RustTraitItemElement>()
            ?: return RustUnknownType
        RustTraitType(trait)
    }

    if (self.and != null) {
        Self = RustReferenceType(Self, mutable = self.mut != null)
    }

    return Self
}

private fun deviseEnumType(variant: RustEnumVariantElement): RustType =
    RustTypificationEngine.typifyItem((variant.parent as RustEnumBodyElement).parent as RustEnumItemElement)

private fun deviseFunctionType(fn: RustFunctionElement): RustFunctionType {
    val paramTypes = mutableListOf<RustType>()

    val self = fn.selfParameter
    if (self != null) {
        paramTypes += deviseSelfType(self)
    }

    paramTypes += fn.valueParameters.map { it.type?.resolvedType ?: RustUnknownType }

    return RustFunctionType(paramTypes, fn.retType?.type?.resolvedType ?: RustUnitType)
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
private fun RustImplItemElement.remapTypeParameters(
    map: Map<RustTypeParameterType, RustType>
): Map<RustTypeParameterType, RustType> =
    type?.resolvedType?.typeParameterValues.orEmpty()
        .mapNotNull {
            val (structParam, structType) = it
            if (structType is RustTypeParameterType) {
                val implType = map[structParam] ?: return@mapNotNull null
                structType to implType
            } else {
                null
            }
        }.toMap()
