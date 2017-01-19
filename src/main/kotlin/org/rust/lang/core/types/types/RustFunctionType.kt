package org.rust.lang.core.types.types

import org.rust.lang.core.types.RustType

data class RustFunctionType(val paramTypes: List<RustType>, val retType: RustType) : RustType {

    override fun toString(): String {
        val params = paramTypes.joinToString(", ", "fn(", ")")
        return if (retType === RustUnitType) params else "$params -> $retType"
    }

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustFunctionType =
        RustFunctionType(paramTypes.map { it.substitute(map) }, retType.substitute(map))
}
