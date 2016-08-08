package org.rust.lang.core.types.util

import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.visitors.impl.RustDecayTypeVisitor

/**
 * Util to get through reference-types if any present
 */
fun RustType.stripAllRefsIfAny(): RustType =
    when (this) {
        is RustReferenceType -> referenced.stripAllRefsIfAny()
        else -> this
    }


/**
 * Deteriorates already resolved type into an unresolved (replacing items with their respective
 * uniquely identifiable fully-qualified crate's paths) one
 */
val RustType.decay: RustUnresolvedType
    get() = accept(RustDecayTypeVisitor())


/**
 * Checks whether this particular type is a primitive one
 */
val RustUnresolvedType.isPrimitive: Boolean
    get() =
        when (this) {
            is RustFloatType,
            is RustIntegerType,
            is RustBooleanType,
            is RustCharacterType,
            is RustStringType -> true

            else -> false
        }

val RustType.isPrimitive: Boolean
    get() = this is RustUnresolvedType && (this as RustUnresolvedType).isPrimitive
