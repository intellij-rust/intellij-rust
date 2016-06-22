package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustReferenceType(val referenced: RustType, val mutable: Boolean = false) : RustType {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitReference(this)

    override fun equals(other: Any?): Boolean = other is RustReferenceType  && other.mutable === mutable
                                                                            && other.referenced === referenced

    override fun hashCode(): Int = referenced.hashCode() * 13577 + 9901

    override fun toString(): String = "${if (mutable) "&mut" else "&"} $referenced"
}
