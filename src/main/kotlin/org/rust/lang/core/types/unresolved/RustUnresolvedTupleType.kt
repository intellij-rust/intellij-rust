package org.rust.lang.core.types.unresolved

import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

class RustUnresolvedTupleType(val elements: Iterable<RustUnresolvedType>) : RustUnresolvedTypeBase() {

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitTupleType(this)

    val types: Iterable<RustUnresolvedType>
        get() = elements

}
