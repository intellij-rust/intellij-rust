package org.rust.lang.core.types.visitors

import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.*

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

    override fun visitInteger(type: RustIntegerType): RustType = type

    override fun visitFloat(type: RustFloatType): RustType = type

    override fun visitString(type: RustStringType): RustType = type

    override fun visitReference(type: RustUnresolvedReferenceType): RustType = RustReferenceType(visit(type.referenced), type.mutable)
}

