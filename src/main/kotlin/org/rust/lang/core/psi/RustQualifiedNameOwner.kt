package org.rust.lang.core.psi

import org.rust.lang.core.symbols.RustPath

interface RustQualifiedNameOwner : RustNamedElement {
    val crateRelativePath: RustPath?
}
