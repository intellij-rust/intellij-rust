package org.rust.lang.core.type

import org.rust.lang.core.type.visitors.RustTypeVisitor

object RustUnknownType : RustType {

    override fun toString(): String = "<unknown type>"

    override fun <T> accept(visitor: RustTypeVisitor<T>): T {
        throw UnsupportedOperationException()
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = 10499
}
