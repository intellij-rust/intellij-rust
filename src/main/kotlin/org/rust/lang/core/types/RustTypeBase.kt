package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.impl.RustEqualityTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustHashCodeComputingTypeVisitor

abstract class RustTypeBase : RustType {

    final override fun equals(other: Any?): Boolean = other is RustType && accept(RustEqualityTypeVisitor(other))

    final override fun hashCode(): Int = accept(RustHashCodeComputingTypeVisitor())

}
