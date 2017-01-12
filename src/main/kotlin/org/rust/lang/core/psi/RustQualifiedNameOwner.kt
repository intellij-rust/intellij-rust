package org.rust.lang.core.psi

import org.rust.lang.core.symbols.RustPath

interface RsQualifiedNamedElement : RsNamedElement {
    val crateRelativePath: RustPath.CrateRelative?
}

val RsQualifiedNamedElement.qualifiedName: String? get() {
    val inCratePath = crateRelativePath ?: return null
    val cargoTarget = containingCargoTarget?.name ?: return null
    return "$cargoTarget$inCratePath"
}
