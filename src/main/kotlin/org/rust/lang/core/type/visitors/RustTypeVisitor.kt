package org.rust.lang.core.type.visitors

import org.rust.lang.core.type.RustStructType
import org.rust.lang.core.type.RustTupleType
import org.rust.lang.core.type.RustUnitType
import org.rust.lang.core.type.RustUnknownType


/**
 * Resolved types visitor trait
 */
interface RustTypeVisitor<T> {

    fun visitStruct(type: RustStructType): T

    fun visitTupleType(type: RustTupleType): T

    fun visitUnknown(type: RustUnknownType): T

    fun visitUnitType(type: RustUnitType): T

}

