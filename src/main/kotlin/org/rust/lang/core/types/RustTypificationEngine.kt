package org.rust.lang.core.types

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.impl.mixin.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.visitors.RustComputingVisitor
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.ResolveEngine
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.types.types.*
import java.io.DataInput

object RustTypificationEngine {

    fun typifyType(type: RsTypeReference): RustType =
        RustTypeTypificationVisitor(type).compute(type)

    fun typifyExpr(expr: RsExpr): RustType =
        RustExprTypificationVisitor().compute(expr)

    fun typifyItem(item: RsItemElement): RustType =
        RustItemTypificationVisitor().compute(item)

    fun typify(named: RsNamedElement): RustType {
        return when (named) {
            is RsItemElement -> typifyItem(named)

            is RsSelfParameter -> deviseSelfType(named)

            is RsPatBinding -> inferPatternBindingType(named)

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
            val base = o.expr?.type
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
            if (variant is RsEnumVariant) {
                return@set variant.parentEnum.type
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
            is RsLiteralKind.String -> RustStringSliceType
            is RsLiteralKind.Char -> RustCharacterType
            null -> RustUnknownType
        }
    }

    override fun visitBlockExpr(o: RsBlockExpr) = set {
        o.block?.type ?: RustUnknownType
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
        o.typeReference?.type ?: RustUnknownType
    }

    override fun visitFunction(o: RsFunction) = set {
        deviseFunctionType(o)
    }

    override fun visitTraitItem(o: RsTraitItem) = set {
        RustTraitType(o)
    }
}

private class RustTypeTypificationVisitor(val pivot: RsTypeReference) : RustComputingVisitor<RustType>() {

    override fun visitTypeReference(o: RsTypeReference) = set {
        RustUnknownType
    }

    override fun visitTupleType(o: RsTupleType) = set {
        // Perhaps introduce tuple_type to PSI?
        if (o.typeReferenceList.size > 0)
            RustTupleType(o.typeReferenceList.map { RustTypificationEngine.typifyType(it) })
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
            .withTypeArguments(typeArguments.map { it.type })
    }

    override fun visitRefLikeType(o: RsRefLikeType) = set {
        if (o.and == null) return@set RustUnknownType //FIXME: handle pointer types
        val base = o.typeReference ?: return@set RustUnknownType
        RustReferenceType(RustTypificationEngine.typifyType(base), o.mut != null)
    }
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

private fun deviseEnumType(variant: RsEnumVariant): RustType =
    RustTypificationEngine.typifyItem((variant.parent as RsEnumBody).parent as RsEnumItem)

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
