package org.rust.lang.core.types

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustReferenceType(val referenced: RustType, val mutable: Boolean = false) : RustTypeBase() {

    override val impls: Sequence<RustImplItemElement>
        get() = referenced.impls + super.impls

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitReference(this)

    override fun toString(): String = "${if (mutable) "&mut" else "&"} $referenced"

}
