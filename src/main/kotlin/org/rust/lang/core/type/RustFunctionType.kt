package org.rust.lang.core.type

import org.rust.lang.core.type.visitors.RustTypeVisitor

class RustFunctionType(val paramTypes: List<RustType>, val retType: RustType) : RustType {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitFunctionType(this)

    override fun equals(other: Any?): Boolean {
        return other is RustFunctionType
            && other.paramTypes.zip(paramTypes).all { it.first == it.second }
            && other.retType == retType
    }

    override fun hashCode(): Int =
        sequenceOf(*paramTypes.toTypedArray(), retType).fold(0, { h, ty -> h * 11173 + ty.hashCode() }) + 8929

}
