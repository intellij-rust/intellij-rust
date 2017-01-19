package org.rust.lang.core.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.impl.mixin.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.visitors.RustComputingVisitor
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.ResolveEngine
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.types.types.*

object RustTypificationEngine {

    fun typifyType(type: RsType): RustType =
        RustTypeTypificationVisitor(type).compute(type)

    fun typifyExpr(expr: RsExpr): RustType =
        RustExprTypificationVisitor().compute(expr)

    fun typifyItem(item: RsItemElement): RustType =
        RustItemTypificationVisitor().compute(item)

    fun typify(named: RsNamedElement): RustType {
        return when (named) {
            is RsItemElement -> typifyItem(named)

            is RsSelfParameter -> deviseSelfType(named)

            is RsPatBinding -> deviseBoundPatType(named)

            is RsEnumVariant -> deviseEnumType(named)

            is RsFunction -> deviseFunctionType(named)

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

    override fun visitUnaryExpr(o: RsUnaryExpr) = set {
        if (o.box != null)
            RustUnknownType
        else {
            val base = o.expr?.resolvedType
                ?: return@set RustUnknownType
            when {
                (o.and != null) -> RustReferenceType(base, o.mut != null)
                (o.mul != null && base is RustReferenceType) -> base.referenced
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
            is RsStructItem -> base.resolvedType
            is RsEnumVariant -> base.parentEnum.resolvedType
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
            if (variant is RsEnumVariant) {
                return@set variant.parentEnum.resolvedType
            }
        }

        val calleeType = fn.resolvedType
        (calleeType as? RustFunctionType)?.retType ?: RustUnknownType
    }

    override fun visitMethodCallExpr(o: RsMethodCallExpr) = set {
        val method = o.reference.resolve() as? RsFunction
            ?: return@set RustUnknownType

        val impl = method.parentOfType<RsImplItem>()
        val typeParameterMap = impl?.remapTypeParameters(o.expr.resolvedType.typeParameterValues).orEmpty()

        deviseFunctionType(method).retType.substitute(typeParameterMap)
    }

    override fun visitFieldExpr(o: RsFieldExpr) = set {
        val field = o.reference.resolve()
        val raw = when (field) {
            is RsFieldDecl -> field.type?.resolvedType
            is RsTupleFieldDecl -> field.type.resolvedType
            else -> null
        } ?: RustUnknownType
        raw.substitute(o.expr.resolvedType.typeParameterValues)
    }

    override fun visitLitExpr(o: RsLitExpr) = set {
        when (o.kind) {
            is RsLiteralKind.Boolean -> RustBooleanType
            is RsLiteralKind.Integer -> RustIntegerType.fromLiteral(o.integerLiteral!!)
            is RsLiteralKind.Float -> RustFloatType.fromLiteral(o.floatLiteral!!)
            is RsLiteralKind.String -> RustStringSliceType
            is RsLiteralKind.Char -> RustCharacterType
        }
    }

    override fun visitBlockExpr(o: RsBlockExpr) = set {
        o.block?.resolvedType ?: RustUnknownType
    }

    override fun visitIfExpr(o: RsIfExpr) = set {
        if (o.elseBranch == null)
            RustUnitType
        else
            o.block?.resolvedType ?: RustUnknownType
    }

    override fun visitWhileExpr(o: RsWhileExpr) = set { RustUnitType }
    override fun visitLoopExpr(o: RsLoopExpr) = set { RustUnitType }
    override fun visitForExpr(o: RsForExpr) = set { RustUnitType }

    override fun visitParenExpr(o: RsParenExpr) = set { o.expr.resolvedType }

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
        val base = o.expr.resolvedType
        // This is super hackish. Need to figure out how to
        // identify known types (See also the CString inspection).
        // Java uses fully qualified names for this, perhaps we
        // can do this as well? Will be harder to test though :(
        if (base is RustEnumType && base.item.name == "Result")
            base.typeArguments.firstOrNull() ?: RustUnknownType
        else
            RustUnknownType
    }

    private val RsBlock.resolvedType: RustType get() = expr?.resolvedType ?: RustUnitType
}

private class RustItemTypificationVisitor : RustComputingVisitor<RustType>() {

    override fun visitElement(element: PsiElement) = set {
        check(element is RsItemElement) {
            "Panic! Should not be used with anything except the inheritors of `RustItemElement` hierarchy!"
        }

        RustUnknownType
    }

    override fun visitStructItem(o: RsStructItem) = set {
        RustStructType(o)
    }

    override fun visitEnumItem(o: RsEnumItem) = set {
        RustEnumType(o)
    }

    override fun visitTypeAlias(o: RsTypeAlias) = set {
        o.type?.resolvedType ?: RustUnknownType
    }

    override fun visitFunction(o: RsFunction) = set {
        deviseFunctionType(o)
    }

    override fun visitTraitItem(o: RsTraitItem) = set {
        RustTraitType(o)
    }
}

private class RustTypeTypificationVisitor(val pivot: RsType) : RustComputingVisitor<RustType>() {

    override fun visitType(o: RsType) = set {
        RustUnknownType
    }

    override fun visitTupleType(o: RsTupleType) = set {
        // Perhaps introduce tuple_type to PSI?
        if (o.typeList.size > 0)
            RustTupleType(o.typeList.map { RustTypificationEngine.typifyType(it) })
        else
            RustUnitType
    }

    override fun visitBaseType(o: RsBaseType) = set {
        val path = o.path?.asRustPath ?: return@set RustUnknownType
        if (path is RustPath.Named && path.segments.isEmpty()) {
            val primitiveType = RustPrimitiveType.fromTypeName(path.head.name)
            if (primitiveType != null) return@set primitiveType
        }
        val target = ResolveEngine.resolve(path, pivot, Namespace.Types)
            .filterIsInstance<RsNamedElement>()
            .firstOrNull() ?: return@set RustUnknownType
        val typeArguments = (path as? RustPath.Named)?.head?.typeArguments.orEmpty()
        RustTypificationEngine.typify(target)
            .withTypeArguments(typeArguments.map { it.resolvedType })
    }

    override fun visitRefLikeType(o: RsRefLikeType) = set {
        if (o.and == null) return@set RustUnknownType //FIXME: handle pointer types
        val base = o.type ?: return@set RustUnknownType
        RustReferenceType(RustTypificationEngine.typifyType(base), o.mut != null)
    }
}

/**
 * NOTA BENE: That's far from complete
 */
private fun deviseBoundPatType(binding: RsPatBinding): RustType {
    //TODO: probably want something more precise than `getTopmostParentOfType` here
    val pattern = PsiTreeUtil.getTopmostParentOfType(binding, RsPat::class.java) ?: return RustUnknownType
    val parent = pattern.parent
    val type = when (parent) {
        is RsLetDecl ->
            // use type ascription, if present or fallback to the type of the initializer expression
            parent.type?.resolvedType ?: parent.expr?.resolvedType

        is RsValueParameter -> parent.type?.resolvedType
        is RsCondition -> parent.expr.resolvedType
        is RsMatchPat -> parent.parentOfType<RsMatchExpr>()?.expr?.resolvedType
        else -> null
    } ?: return RustUnknownType

    return RustTypeInferenceEngine.inferPatBindingTypeFrom(binding, pattern, type)
}

/**
 * Devises type for the given (implicit) self-argument
 */
private fun deviseSelfType(self: RsSelfParameter): RustType {
    val impl = self.parentOfType<RsImplItem>()
    var Self: RustType = if (impl != null) {
        impl.type?.resolvedType ?: return RustUnknownType
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

private fun deviseEnumType(variant: RsEnumVariant): RustType =
    RustTypificationEngine.typifyItem((variant.parent as RsEnumBody).parent as RsEnumItem)

private fun deviseFunctionType(fn: RsFunction): RustFunctionType {
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
private fun RsImplItem.remapTypeParameters(
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
