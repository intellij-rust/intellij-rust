package org.rust.lang.core.psi

import org.rust.lang.core.types.RustType

val RustPathElement.isPrimitive: Boolean get() = path == null && referenceName in RustType.PRIMITIVE_TYPES

