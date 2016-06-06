package org.rust.lang.core.type.visitors

import org.rust.lang.core.type.RustUnitType
import org.rust.lang.core.type.RustUnknownType
import org.rust.lang.core.type.unresolved.RustUnresolvedTupleType
import org.rust.lang.core.type.unresolved.RustUnresolvedPathType

/**
 * Unresolved types visitor trait
 */
interface RustUnresolvedTypeVisitor<T> {

    fun visitPathType(type: RustUnresolvedPathType): T

    fun visitTupleType(type: RustUnresolvedTupleType): T

    fun visitUnitType(type: RustUnitType): T

    fun visitUnknown(type: RustUnknownType): T

}
