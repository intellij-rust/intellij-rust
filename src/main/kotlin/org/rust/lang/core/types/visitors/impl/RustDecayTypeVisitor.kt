package org.rust.lang.core.types.visitors.impl

import org.rust.lang.core.psi.canonicalCratePath
import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.*
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustDecayTypeVisitor : RustTypeVisitor<RustUnresolvedType> {

    private fun visit(type: RustType): RustUnresolvedType =
        type.accept(this)

    private fun visitTypeList(types: Iterable<RustType>): Iterable<RustUnresolvedType> =
        types.map { visit(it) }

    override fun visitStruct(type: RustStructType): RustUnresolvedType = RustUnresolvedPathType(type.struct.canonicalCratePath)

    override fun visitEnum(type: RustEnumType): RustUnresolvedType = RustUnresolvedPathType(type.enum.canonicalCratePath)

    override fun visitReference(type: RustReferenceType): RustUnresolvedType =
        RustUnresolvedReferenceType(referenced = visit(type.referenced), mutable = type.mutable)

    override fun visitTupleType(type: RustTupleType): RustUnresolvedType =
        RustUnresolvedTupleType(visitTypeList(type.types))

    override fun visitFunctionType(type: RustFunctionType): RustUnresolvedType =
        RustUnresolvedFunctionType(visitTypeList(type.paramTypes).toList(), visit(type.retType))

    override fun visitUnknown(type: RustUnknownType): RustUnresolvedType = type

    override fun visitUnitType(type: RustUnitType): RustUnresolvedType = type

    override fun visitInteger(type: RustIntegerType): RustUnresolvedType = type

    override fun visitFloat(type: RustFloatType): RustUnresolvedType = type

    override fun visitString(type: RustStringType): RustUnresolvedType = type

    override fun visitChar(type: RustCharacterType): RustUnresolvedType = type

    override fun visitBoolean(type: RustBooleanType): RustUnresolvedType = type
}
