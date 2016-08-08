package org.rust.lang.core.types.visitors

import org.rust.lang.core.types.*

abstract class RustTypeVisitorWithDefaults<T> : RustTypeVisitor<T> {

    protected abstract fun visitByDefault(type: RustType): T

    override fun visitStruct(type: RustStructType): T = visitByDefault(type)

    override fun visitTupleType(type: RustTupleType): T = visitByDefault(type)

    override fun visitUnknown(type: RustUnknownType): T = visitByDefault(type)

    override fun visitUnitType(type: RustUnitType): T = visitByDefault(type)

    override fun visitFunctionType(type: RustFunctionType): T = visitByDefault(type)

    override fun visitEnum(type: RustEnumType): T = visitByDefault(type)

    override fun visitInteger(type: RustIntegerType): T = visitByDefault(type)

    override fun visitReference(type: RustReferenceType): T = visitByDefault(type)

    override fun visitFloat(type: RustFloatType): T = visitByDefault(type)

    override fun visitString(type: RustStringType): T = visitByDefault(type)

    override fun visitChar(type: RustCharacterType): T = visitByDefault(type)

    override fun visitBoolean(type: RustBooleanType): T = visitByDefault(type)

}
