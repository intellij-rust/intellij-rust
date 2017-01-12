package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.RustTypeVisitor

object RustBooleanType : RustPrimitiveTypeBase() {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitBoolean(this)

    override fun toString(): String = "bool"

}
