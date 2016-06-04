package org.rust.lang.core.psi.visitors

import org.rust.lang.core.psi.RustPathExprElement
import org.rust.lang.core.psi.RustPathTypeElement
import org.rust.lang.core.type.unresolved.RustUnresolvedPathType
import org.rust.lang.core.type.unresolved.RustUnresolvedType

class RustTypificationVisitor : RustRecursiveElementVisitor() {

    private lateinit var cur: RustUnresolvedType

    val inferred: RustUnresolvedType
        get() = cur

    override fun visitPathType(o: RustPathTypeElement) {
        o.path?.let {
            cur = RustUnresolvedPathType(it)
        }
    }

    override fun visitPathExpr(o: RustPathExprElement) {
        cur = RustUnresolvedPathType(o.path)
    }
}
