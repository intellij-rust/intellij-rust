package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustTupleType(val elements: Iterable<RustType>) : RustType {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTupleType(this)

    override fun equals(other: Any?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun hashCode(): Int {
        throw UnsupportedOperationException()
    }

}

