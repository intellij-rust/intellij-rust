package org.rust.lang.core.psi

import org.rust.lang.core.symbols.RustPath

interface RustQualifiedNameOwner : RustNamedElement {
    val crateRelativePath: RustPath.CrateRelative?
}

val RustQualifiedNameOwner.qualifiedName: String? get() {
    val inCratePath = crateRelativePath ?: return null
    val cargoTarget = containingCargoTarget?.name ?: return null
    return "$cargoTarget$inCratePath"
}
