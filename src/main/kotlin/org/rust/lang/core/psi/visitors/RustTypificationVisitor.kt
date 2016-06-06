package org.rust.lang.core.psi.visitors

import org.rust.lang.core.psi.*
import org.rust.lang.core.type.RustUnitType
import org.rust.lang.core.type.RustUnknownType
import org.rust.lang.core.type.unresolved.RustUnresolvedPathType
import org.rust.lang.core.type.unresolved.RustUnresolvedTupleType
import org.rust.lang.core.type.unresolved.RustUnresolvedType
import org.rust.lang.core.type.util.type

class RustTypificationVisitor : RustRecursiveElementVisitor() {

    private lateinit var cur: RustUnresolvedType

    val inferred: RustUnresolvedType
        get() = cur

    /**
     * NOTA BENE: This is to prevent [cur] from being accessed, prior to being initialized
     */
    override fun visitExpr(o: RustExprElement) {
        cur = RustUnknownType
    }

    override fun visitPathType(o: RustPathTypeElement) {
        o.path?.let {
            cur = RustUnresolvedPathType(it)
        }
    }

    override fun visitPathExpr(o: RustPathExprElement) {
        cur = RustUnresolvedPathType(o.path)
    }

    override fun visitStructExpr(o: RustStructExprElement) {
        cur = RustUnresolvedPathType(o.path)
    }

    override fun visitTupleExpr(o: RustTupleExprElement) {
        cur = RustUnresolvedTupleType(o.exprList.map { it.type })
    }

    override fun visitUnitExpr(o: RustUnitExprElement) {
        cur = RustUnitType
    }
}
