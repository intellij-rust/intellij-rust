/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

interface RsQualifiedNamedElement : RsNamedElement {
    val crateRelativePath: String?
}

val RsQualifiedNamedElement.qualifiedName: String? get() {
    val inCratePath = crateRelativePath ?: return null
    val cargoTarget = containingCargoTarget?.normName ?: return null
    return "$cargoTarget$inCratePath"
}
