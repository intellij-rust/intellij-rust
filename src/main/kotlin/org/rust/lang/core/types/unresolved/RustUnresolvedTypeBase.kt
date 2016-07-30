package org.rust.lang.core.types.unresolved

import org.rust.lang.core.types.visitors.impl.RustEqualityUnresolvedTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustHashCodeComputingUnresolvedTypeVisitor

abstract class RustUnresolvedTypeBase : RustUnresolvedType {

    final override fun equals(other: Any?): Boolean =
        other is RustUnresolvedType && accept(RustEqualityUnresolvedTypeVisitor(other))

    final override fun hashCode(): Int =
        accept(RustHashCodeComputingUnresolvedTypeVisitor())

}
