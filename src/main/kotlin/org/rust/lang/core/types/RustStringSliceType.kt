package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.RustTypeVisitor

object RustStringSliceType : RustPrimitiveTypeBase() {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitString(this)

    override fun toString(): String = "str"
}
