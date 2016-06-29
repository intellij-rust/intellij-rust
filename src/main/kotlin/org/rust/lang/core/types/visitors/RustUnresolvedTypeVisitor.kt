package org.rust.lang.core.types.visitors

import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.RustUnresolvedFunctionType
import org.rust.lang.core.types.unresolved.RustUnresolvedPathType
import org.rust.lang.core.types.unresolved.RustUnresolvedReferenceType
import org.rust.lang.core.types.unresolved.RustUnresolvedTupleType

/**
 * Unresolved types visitor trait
 */
interface RustUnresolvedTypeVisitor<T> {

    fun visitPathType(type: RustUnresolvedPathType): T

    fun visitTupleType(type: RustUnresolvedTupleType): T

    fun visitUnitType(type: RustUnitType): T

    fun visitUnknown(type: RustUnknownType): T

    fun visitFunctionType(type: RustUnresolvedFunctionType): T

    fun visitInteger(type: RustIntegerType): T

    fun visitReference(type: RustUnresolvedReferenceType): T

    fun visitFloat(type: RustFloatType): T

    fun visitString(type: RustStringType): T

}
