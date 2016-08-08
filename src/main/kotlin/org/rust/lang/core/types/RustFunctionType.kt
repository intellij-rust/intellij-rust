package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustFunctionType(val paramTypes: List<RustType>, val retType: RustType) : RustTypeBase() {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitFunctionType(this)

    override fun toString(): String {
        val params = paramTypes.joinToString(", ", "fn(", ")")
        return if (retType === RustUnitType) params else "$params -> $retType"
    }
}
