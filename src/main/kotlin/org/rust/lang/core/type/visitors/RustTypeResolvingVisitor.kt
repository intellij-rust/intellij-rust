package org.rust.lang.core.type.visitors

import org.rust.lang.core.type.*
import org.rust.lang.core.type.unresolved.RustUnresolvedFunctionType
import org.rust.lang.core.type.unresolved.RustUnresolvedPathType
import org.rust.lang.core.type.unresolved.RustUnresolvedTupleType
import org.rust.lang.core.type.unresolved.RustUnresolvedType

open class RustTypeResolvingVisitor : RustUnresolvedTypeVisitor<RustType> {

    private fun visit(type: RustUnresolvedType): RustType = type.accept(this)

    override fun visitUnknown(type: RustUnknownType): RustType = RustUnknownType

    override fun visitUnitType(type: RustUnitType): RustType = RustUnitType

    override fun visitTupleType(type: RustUnresolvedTupleType): RustType =
        RustTupleType(type.elements.map { visit(it)})

    override fun visitPathType(type: RustUnresolvedPathType): RustType =
        type.path.reference.resolve()?.let { RustTypificationEngine.typify(it) } ?: RustUnknownType

    override fun visitFunctionType(type: RustUnresolvedFunctionType): RustType =
        RustFunctionType(type.paramTypes.map { visit(it) }, visit(type.retType))

}

