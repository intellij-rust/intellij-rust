package org.rust.lang.core.type

import org.rust.lang.core.type.visitors.RustTypeVisitor

open class RustImplType(val type: RustType) : RustType {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T =
        visitor.visitImpl(this)
}
