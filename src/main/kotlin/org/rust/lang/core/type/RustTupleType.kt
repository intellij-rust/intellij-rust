package org.rust.lang.core.type

import org.rust.lang.core.type.visitors.RustTypeVisitor

class RustTupleType(val elements: Iterable<RustType>) : RustType {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTupleType(this)

    override fun equals(other: Any?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun hashCode(): Int {
        throw UnsupportedOperationException()
    }

}

