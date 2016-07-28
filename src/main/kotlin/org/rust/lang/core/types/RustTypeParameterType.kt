package org.rust.lang.core.types

import org.rust.lang.core.psi.RustTypeParamElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustTypeParameterType(val parameter: RustTypeParamElement) : RustType {
    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTypeParameter(this)

    override fun toString(): String = parameter.name ?: "<unknown>"

    override fun equals(other: Any?): Boolean = other is RustTypeParameterType && other.parameter === parameter

    override fun hashCode(): Int = parameter.hashCode()
}
