package org.rust.lang.core.types.util

import org.rust.lang.core.types.RustReferenceType
import org.rust.lang.core.types.RustType
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
