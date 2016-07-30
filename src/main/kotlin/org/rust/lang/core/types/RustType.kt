package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.RustTypeVisitor

interface RustType {

    fun <T> accept(visitor: RustTypeVisitor<T>): T

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String

}
