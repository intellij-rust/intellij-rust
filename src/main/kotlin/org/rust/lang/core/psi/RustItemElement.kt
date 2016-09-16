package org.rust.lang.core.psi

import org.rust.lang.core.symbols.RustPath

interface RustItemElement : RustVisibilityOwner, RustOuterAttributeOwner

val RustItemElement.canonicalCratePath: RustPath? get() {
    if (this is RustMod) {
        @Suppress("USELESS_CAST")
        return (this as RustMod).canonicalCratePath
    }
    val name = name ?: return null
    return containingMod?.canonicalCratePath?.join(name)
}

