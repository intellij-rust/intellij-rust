package org.rust.lang.core.type

import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.type.visitors.RustTypeVisitor

class RustTraitImplType(
    val trait:  RustTraitItemElement,
        type:   RustType
) : RustImplType(type) {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T =
        visitor.visitTraitImpl(this)
}
