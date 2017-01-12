package org.rust.lang.core.types.util

import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.RustUnresolvedType

/**
 * Util to get through reference-types if any present
 */
fun RustType.stripAllRefsIfAny(): RustType = when (this) {
    is RustReferenceType -> referenced.stripAllRefsIfAny()
    else -> this
}


/**
 * Checks whether this particular type is a primitive one
 */
val RustUnresolvedType.isPrimitive: Boolean get() = when (this) {
    is RustFloatType,
    is RustIntegerType,
    is RustBooleanType,
    is RustCharacterType,
    is RustStringSliceType -> true
    else -> false
}
