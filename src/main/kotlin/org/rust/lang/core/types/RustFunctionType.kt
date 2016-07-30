package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustFunctionType(val paramTypes: List<RustType>, val retType: RustType) : RustTypeBase() {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitFunctionType(this)

//    override fun equals(other: Any?): Boolean {
//        return other is RustFunctionType
//            && other.paramTypes == paramTypes
//            && other.retType == retType
//    }
//
//    override fun hashCode(): Int =
//        sequenceOf(*paramTypes.toTypedArray(), retType).fold(0, { h, ty -> h * 11173 + ty.hashCode() }) + 8929

    override fun toString(): String {
        val params = paramTypes.joinToString(", ", "fn(", ")")
        return if (retType === RustUnitType) params else "$params -> $retType"
    }
}
