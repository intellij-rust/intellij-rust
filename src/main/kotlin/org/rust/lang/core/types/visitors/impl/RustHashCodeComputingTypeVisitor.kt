package org.rust.lang.core.types.visitors.impl

import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.RustUnresolvedFunctionType
import org.rust.lang.core.types.unresolved.RustUnresolvedPathType
import org.rust.lang.core.types.unresolved.RustUnresolvedReferenceType
import org.rust.lang.core.types.unresolved.RustUnresolvedTupleType
import org.rust.lang.core.types.visitors.RustInvariantTypeVisitor
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor


class RustHashCodeComputingTypeVisitor
    : RustHashCodeComputingTypeVisitorBase()
    , RustTypeVisitor<Int> {

    override fun visitStruct(type: RustStructType): Int = type.struct.hashCode() * 10067 + 9631

    override fun visitEnum(type: RustEnumType): Int = type.enum.hashCode() * 12289 + 9293

    override fun visitTupleType(type: RustTupleType): Int = type.types.hashCode()

    override fun visitFunctionType(type: RustFunctionType): Int =
        sequenceOf(*type.paramTypes.toTypedArray(), type.retType).fold(0, { h, ty -> h * 11173 + ty.hashCode() }) + 8929

    override fun visitReference(type: RustReferenceType): Int =
        type.referenced.hashCode() * 13577 + (if (type.mutable) 3331 else 0) + 9901

}


open class RustHashCodeComputingUnresolvedTypeVisitor
    : RustHashCodeComputingTypeVisitorBase()
    , RustUnresolvedTypeVisitor<Int> {

    override fun visitPathType(type: RustUnresolvedPathType): Int = type.path.hashCode()

    override fun visitTupleType(type: RustUnresolvedTupleType): Int = type.types.hashCode()

    override fun visitFunctionType(type: RustUnresolvedFunctionType): Int =
        sequenceOf(*type.paramTypes.toTypedArray(), type.retType).fold(0, { h, ty -> h * 13591 + ty.hashCode() }) + 9187

    override fun visitReference(type: RustUnresolvedReferenceType): Int =
        type.referenced.hashCode() * 10103 + (if (type.mutable) 5953 else 0) + 11159

}


open class RustHashCodeComputingTypeVisitorBase : RustInvariantTypeVisitor<Int> {

    override fun visitInteger(type: RustIntegerType): Int = type.kind.hashCode()

    override fun visitFloat(type: RustFloatType): Int = type.kind.hashCode()

    override fun visitUnitType(type: RustUnitType): Int = 9049

    override fun visitString(type: RustStringType): Int = 10709

    override fun visitChar(type: RustCharacterType): Int = 10099

    override fun visitBoolean(type: RustBooleanType): Int = 10427

    override fun visitUnknown(type: RustUnknownType): Int = 10499

}
