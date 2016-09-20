package org.rust.lang.core.psi

import org.rust.lang.core.symbols.RustPath

interface RustPathNamedElement: RustNamedElement {
    val canonicalCratePath: RustPath?
}
