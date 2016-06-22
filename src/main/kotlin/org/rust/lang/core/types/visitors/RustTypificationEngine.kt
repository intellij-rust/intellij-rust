package org.rust.lang.core.types.visitors

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.visitors.RustRecursiveElementVisitor
import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.RustUnresolvedPathType
import org.rust.lang.core.types.unresolved.RustUnresolvedTupleType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.util.type

object RustTypificationEngine {

    fun typifyType(type: RustTypeElement): RustUnresolvedType {
        val v = RustTypeTypificationVisitor()
        type.accept(v)
        return v.inferred
    }

    fun typifyExpr(expr: RustExprElement): RustType {
        val v = RustExprTypificationVisitor()
        expr.accept(v)
        return v.inferred
    }

    fun typifyItem(item: RustItemElement): RustType {
        val v = RustItemTypificationVisitor()
        item.accept(v)
        return v.inferred
    }

    fun typify(named: RustNamedElement): RustType {
        return when (named) {
            is RustItemElement -> typifyItem(named)

            is RustSelfArgumentElement -> deviseSelfType(named)

            is RustPatBindingElement -> deviseBoundPatType(named)

            is RustFnElement -> typifyFn(named)

            else -> RustUnknownType
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
            else -> null
        } ?: return RustUnknownType

        return RustTypeInferenceEngine.inferPatBindingTypeFrom(binding, pattern, type)
    }

    /**
     * Devises type for the given (implicit) self-argument
     */
    private fun deviseSelfType(self: RustSelfArgumentElement): RustType =
        self.parentOfType<RustImplItemElement>()?.type?.resolvedType ?: RustUnknownType
}

private open class RustTypificationVisitorBase<T: Any> : RustRecursiveElementVisitor() {

    protected lateinit var cur: T

    val inferred: T
        get() = cur

}

private class RustExprTypificationVisitor : RustTypificationVisitorBase<RustType>() {

    override fun visitElement(element: PsiElement) {
        throw UnsupportedOperationException("Panic! Should not be used with anything except the inheritors of `RustExprElement` hierarchy!")
    }

    /**
     * NOTA BENE: This is to prevent [cur] from being accessed, prior to being initialized
     */
    override fun visitExpr(o: RustExprElement) {
        cur = RustUnknownType
    }

    override fun visitPathExpr(o: RustPathExprElement) {
        cur = o.path.reference.resolve()?.let { RustTypificationEngine.typify(it) } ?: RustUnknownType
    }

    override fun visitStructExpr(o: RustStructExprElement) {
        cur = o.path.reference.resolve() .let { it as? RustStructItemElement }
                                        ?.let { RustStructType(it) } ?: RustUnknownType
    }

    override fun visitTupleExpr(o: RustTupleExprElement) {
        cur = RustTupleType(o.exprList.map { RustTypificationEngine.typifyExpr(it) })
    }

    override fun visitUnitExpr(o: RustUnitExprElement) {
        cur = RustUnitType
    }

    override fun visitCallExpr(o: RustCallExprElement) {
        val calleeType = o.expr.resolvedType
        if (calleeType is RustFunctionType) {
            cur = calleeType.retType
            return
        }

        cur = RustUnknownType
    }

    override fun visitMethodCallExpr(o: RustMethodCallExprElement) {
        val ref = o.reference!!
        val method = ref.resolve()
        if (method is RustImplMethodMemberElement)
            method.retType?.type?.let {
                cur = it.resolvedType
                return
            }

        cur = RustUnknownType
    }
}

private class RustItemTypificationVisitor : RustTypificationVisitorBase<RustType>() {

    override fun visitElement(element: PsiElement) {
        check(element is RustItemElement) {
           "Panic! Should not be used with anything except the inheritors of `RustItemElement` hierarchy!"
        }

        cur = RustUnknownType
    }

    override fun visitStructItem(o: RustStructItemElement) {
        cur = RustStructType(o)
    }

    override fun visitFnItem(o: RustFnItemElement) {
        cur = typifyFn(o)
    }
}

private class RustTypeTypificationVisitor : RustTypificationVisitorBase<RustUnresolvedType>() {

    override fun visitElement(element: PsiElement) {
        throw UnsupportedOperationException("Panic! Should not be used with anything except the inheritors of `RustTypeElement` hierarchy!")
    }

    override fun visitType(o: RustTypeElement) {
        cur = RustUnknownType
    }

    override fun visitTupleType(o: RustTupleTypeElement) {
        // Perhaps introduce tuple_type to PSI?
        cur = if (o.typeList.size > 0)
            RustUnresolvedTupleType(o.typeList.map { it.type })
        else
            RustUnitType
    }

    override fun visitPathType(o: RustPathTypeElement) {
        cur = o.path?.let { RustIntegerType.from(it.text) ?: RustUnresolvedPathType(it) } ?: RustUnknownType
    }

}

private fun typifyFn(fn: RustFnElement): RustType {
    if (!fn.isStatic) return RustUnknownType

    return RustFunctionType(
        fn.parameters?.parameterList.orEmpty().map { it.type?.resolvedType ?: RustUnknownType },
        fn.retType?.type?.resolvedType ?: RustUnitType
    )
}
