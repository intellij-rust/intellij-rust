package org.rust.lang.core.type.unresolved

import org.rust.lang.core.psi.RustPathElement
import org.rust.lang.core.type.visitors.RustUnresolvedTypeVisitor

class RustUnresolvedPathType(val path: RustPathElement) : RustUnresolvedType {

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visit(this)

}
