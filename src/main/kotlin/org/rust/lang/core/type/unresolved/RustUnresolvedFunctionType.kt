package org.rust.lang.core.type.unresolved

import org.rust.lang.core.type.visitors.RustUnresolvedTypeVisitor

class RustUnresolvedFunctionType(
    val paramTypes: List<RustUnresolvedType>,
    val retType: RustUnresolvedType
) : RustUnresolvedType {

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitFunctionType(this)

}
