package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustTypeParameterType(val parameter: RustTypeParamElement) : RustTypeBase() {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTypeParameter(this)

    override fun toString(): String = parameter.name ?: "<unknown>"

}
