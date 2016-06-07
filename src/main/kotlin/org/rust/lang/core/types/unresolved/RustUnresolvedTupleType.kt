package org.rust.lang.core.types.unresolved

import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

class RustUnresolvedTupleType(val elements: Iterable<RustUnresolvedType>) : RustUnresolvedType {

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitTupleType(this)

}
