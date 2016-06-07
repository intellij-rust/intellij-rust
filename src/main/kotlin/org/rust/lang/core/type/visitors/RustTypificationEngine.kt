package org.rust.lang.core.type.visitors

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.visitors.RustRecursiveElementVisitor
import org.rust.lang.core.type.*
import org.rust.lang.core.type.unresolved.RustUnresolvedPathType
import org.rust.lang.core.type.unresolved.RustUnresolvedType
import org.rust.lang.core.type.util.resolvedType

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

            else -> RustUnknownType
        }
    }

    /**
     * NOTA BENE: That's far from complete
     */
    private fun deviseBoundPatType(pat: RustPatBindingElement): RustType {
        val letDecl = pat.parentOfType<RustLetDeclElement>()
        if (letDecl != null) {
            val typeAsc = letDecl.type
            if (typeAsc != null)
                return typeAsc.resolvedType

            val initExpr = letDecl.expr
            if (initExpr != null)
                return RustTypeInferenceEngine.inferPatBindingTypeFrom(pat, letDecl.pat!!, initExpr.resolvedType)
        }

        return RustUnknownType
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

    override fun visitElement(element: PsiElement?) {
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

}

private class RustItemTypificationVisitor : RustTypificationVisitorBase<RustType>() {

    override fun visitElement(element: PsiElement?) {
        throw UnsupportedOperationException("Panic! Should not be used with anything except the inheritors of `RustExprElement` hierarchy!")
    }

    override fun visitItem(o: RustItemElement) {
        cur = RustUnknownType
    }

    override fun visitStructItem(o: RustStructItemElement) {
        cur = RustStructType(o)
    }
}

private class RustTypeTypificationVisitor : RustTypificationVisitorBase<RustUnresolvedType>() {

    override fun visitElement(element: PsiElement?) {
        throw UnsupportedOperationException("Panic! Should not be used with anything except the inheritors of `RustTypeElement` hierarchy!")
    }

    override fun visitType(o: RustTypeElement) {
        cur = RustUnknownType
    }

    override fun visitPathType(o: RustPathTypeElement) {
        o.path?.let {
            cur = RustUnresolvedPathType(it)
        }
    }

}
