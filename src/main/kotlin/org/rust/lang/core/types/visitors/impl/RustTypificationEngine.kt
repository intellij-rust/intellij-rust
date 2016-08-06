package org.rust.lang.core.types.visitors.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.parentEnum
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.visitors.RustComputingVisitor
import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.RustUnresolvedPathType
import org.rust.lang.core.types.unresolved.RustUnresolvedReferenceType
import org.rust.lang.core.types.unresolved.RustUnresolvedTupleType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.util.type
import org.rust.lang.core.types.visitors.impl.RustTypeInferenceEngine

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

            is RustSelfArgumentElement -> deviseSelfType(named)

            is RustPatBindingElement -> deviseBoundPatType(named)

            is RustEnumVariantElement -> deviseEnumType(named)

            is RustFnElement -> deviseFunctionType(named)

            is RustTypeParamElement -> RustTypeParameterType(named)

            else -> RustUnknownType
        }
    }
}

private class RustExprTypificationVisitor : RustComputingVisitor<RustType>() {

    override fun visitExpr(o: RustExprElement) = set {
        // Default until we handle all the cases explicitly
        RustUnknownType
    }

    override fun visitPathExpr(o: RustPathExprElement) = set {
        o.path.reference.resolve()?.let { RustTypificationEngine.typify(it) } ?: RustUnknownType
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
        if (calleeType is RustFunctionType)
            calleeType.retType
        else
            RustUnknownType
    }

    override fun visitMethodCallExpr(o: RustMethodCallExprElement) = set {
        val method = o.reference.resolve() as? RustFnElement
        method?.let { deviseFunctionType(it).retType } ?: RustUnknownType
    }

    override fun visitFieldExpr(o: RustFieldExprElement) = set {
        val field = o.reference.resolve() as? RustFieldDeclElement
        field?.type?.resolvedType ?: RustUnknownType
    }

    override fun visitLitExpr(o: RustLitExprElement) = set {
        when {
            o.integerLiteral    != null -> RustIntegerType.deduceBySuffix(o.text)   ?: RustIntegerType.deduceUnsuffixed(o)
            o.floatLiteral      != null -> RustFloatType.deduceBySuffix(o.text)     ?: RustFloatType.deduceUnsuffixed(o)
            o.stringLiteral     != null -> RustStringType
            o.charLiteral       != null -> RustCharacterType

            o.`true`            != null ||
            o.`false`           != null -> RustBooleanType

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

    override fun visitBinaryExpr(o: RustBinaryExprElement) {
        super.visitBinaryExpr(o)
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

    override fun visitTypeItem(o: RustTypeItemElement) = set {
        o.type.resolvedType
    }

    override fun visitFnItem(o: RustFnItemElement) = set {
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

    override fun visitPathType(o: RustPathTypeElement) = set {
        o.path?.let {
            (   RustIntegerType.deduceBySuffix(it.text) ?:
                RustFloatType.deduceBySuffix(it.text)   ?:
                RustBooleanType.deduce(it.text)         ?:
                RustUnresolvedPathType(it)
                ) as RustUnresolvedType
        } ?: RustUnknownType
    }

    override fun visitRefType(o: RustRefTypeElement) = set {
        o.type?.let { RustUnresolvedReferenceType(it.type , o.mut != null) } ?: RustUnknownType
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

        is RustParameterElement -> parent.type?.resolvedType
        is RustScopedLetDeclElement -> parent.expr.resolvedType
        else -> null
    } ?: return RustUnknownType

    return RustTypeInferenceEngine.inferPatBindingTypeFrom(binding, pattern, type)
}

/**
 * Devises type for the given (implicit) self-argument
 */
private fun deviseSelfType(self: RustSelfArgumentElement): RustType {
    var Self = self.parentOfType<RustImplItemElement>()?.type?.resolvedType ?: return RustUnknownType

    if (self.and != null)
        Self = RustReferenceType(Self, mutable = self.mut != null)

    return Self
}

private fun deviseEnumType(variant: RustEnumVariantElement): RustType =
    RustTypificationEngine.typifyItem((variant.parent as RustEnumBodyElement).parent as RustEnumItemElement)

private fun deviseFunctionType(fn: RustFnElement): RustFunctionType {
    var paramTypes = emptyList<RustType>()

    val params = fn.parameters
    if (params != null) {
        val self = params.selfArgument
        if (self != null)
            paramTypes += deviseSelfType(self)

        paramTypes += params.parameterList.orEmpty().map { it.type?.resolvedType ?: RustUnknownType }
    }

    return RustFunctionType(
        paramTypes  = paramTypes,
        retType     = fn.retType?.type?.resolvedType ?: RustUnitType
    )
}
