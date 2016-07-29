package org.rust.lang.core.types.unresolved

import org.rust.lang.core.types.RustUnitType
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

class RustUnresolvedFunctionType(
    val paramTypes: List<RustUnresolvedType>,
    val retType: RustUnresolvedType
) : RustUnresolvedTypeBase() {

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitFunctionType(this)

    override fun toString(): String {
        val params = paramTypes.joinToString(", ", "fn(", ")")
        return if (retType === RustUnitType) params else "$params -> $retType"
    }
}
