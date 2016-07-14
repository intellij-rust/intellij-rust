package org.rust.lang.core.types

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustReferenceType(val referenced: RustType, val mutable: Boolean = false) : RustType {

    override val inherentImpls: Sequence<RustImplItemElement>
        get() = referenced.inherentImpls + super.inherentImpls

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitReference(this)

    override fun equals(other: Any?): Boolean = other is RustReferenceType  && other.mutable === mutable
                                                                            && other.referenced === referenced

    override fun hashCode(): Int =
        referenced.hashCode() * 13577 + (if (mutable) 3331 else 0) + 9901

    override fun toString(): String = "${if (mutable) "&mut" else "&"} $referenced"

    override val baseTypeName: String? get() = referenced.baseTypeName
}
