package org.rust.lang.core.type

import org.rust.lang.core.type.visitors.RustTypeVisitor

object RustUnknownType : RustType {

    override fun toString(): String = "<unknown type>"

    override fun <T> accept(visitor: RustTypeVisitor<T>): T {
        throw UnsupportedOperationException()
    }
}
