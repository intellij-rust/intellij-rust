package org.rust.lang.core.types.visitors

import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.*


abstract class RustUnresolvedTypeVisitorWithDefaults<T> : RustUnresolvedTypeVisitor<T> {

    protected abstract fun visitByDefault(type: RustUnresolvedType): T

    override fun visitPathType(type: RustUnresolvedPathType): T = visitByDefault(type)

    override fun visitTupleType(type: RustUnresolvedTupleType): T = visitByDefault(type)

    override fun visitUnitType(type: RustUnitType): T = visitByDefault(type)

    override fun visitUnknown(type: RustUnknownType): T = visitByDefault(type)

    override fun visitFunctionType(type: RustUnresolvedFunctionType): T = visitByDefault(type)

    override fun visitInteger(type: RustIntegerType): T = visitByDefault(type)

    override fun visitReference(type: RustUnresolvedReferenceType): T = visitByDefault(type)

    override fun visitFloat(type: RustFloatType): T = visitByDefault(type)

    override fun visitString(type: RustStringType): T = visitByDefault(type)

    override fun visitChar(type: RustCharacterType): T = visitByDefault(type)

    override fun visitBoolean(type: RustBooleanType): T = visitByDefault(type)

}
