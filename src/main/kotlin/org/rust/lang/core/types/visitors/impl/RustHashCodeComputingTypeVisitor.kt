package org.rust.lang.core.types.visitors.impl

import org.rust.lang.core.types.*
import org.rust.lang.core.types.visitors.RustInvariantTypeVisitor
import org.rust.lang.core.types.visitors.RustTypeVisitor


open class RustHashCodeComputingTypeVisitor
    : RustHashCodeComputingTypeVisitorBase()
    , RustTypeVisitor<Int> {
    protected fun visit(type: RustType): Int = type.accept(this)

    override fun visitStruct(type: RustStructType): Int = type.item.hashCode() * 10067 + 9631

    override fun visitEnum(type: RustEnumType): Int = type.item.hashCode() * 12289 + 9293

    override fun visitTypeParameter(type: RustTypeParameterType): Int = type.parameter.hashCode() * 13859 + 9419

    override fun visitTrait(type: RustTraitType): Int = type.trait.hashCode() * 12757 + 10061

    override fun visitTupleType(type: RustTupleType): Int =
        type.types.fold(0, { h, ty -> h * 8741 + visit(ty) }) + 17387

    override fun visitFunctionType(type: RustFunctionType): Int =
        sequenceOf(*type.paramTypes.toTypedArray(), type.retType).fold(0, { h, ty -> h * 11173 + visit(ty) }) + 8929

    override fun visitReference(type: RustReferenceType): Int =
        visit(type.referenced) * 13577 + (if (type.mutable) 3331 else 0) + 9901

}


open class RustHashCodeComputingTypeVisitorBase : RustInvariantTypeVisitor<Int> {

    override fun visitInteger(type: RustIntegerType): Int = type.kind.hashCode()

    override fun visitFloat(type: RustFloatType): Int = type.kind.hashCode()

    override fun visitUnitType(type: RustUnitType): Int = 9049

    override fun visitString(type: RustStringSliceType): Int = 10709

    override fun visitChar(type: RustCharacterType): Int = 10099

    override fun visitBoolean(type: RustBooleanType): Int = 10427

    override fun visitUnknown(type: RustUnknownType): Int = 10499

}
