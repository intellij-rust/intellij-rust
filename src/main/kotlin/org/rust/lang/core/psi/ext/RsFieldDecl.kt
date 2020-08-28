/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsTypeReference

val RsFieldDecl.owner: RsFieldsOwner? get() = stubAncestorStrict()

val RsFieldDecl.escapedName: String? get() = (this as? RsNamedElement)?.escapedName ?: name

interface RsFieldDecl : RsOuterAttributeOwner, RsVisibilityOwner {
    val typeReference: RsTypeReference?

    @JvmDefault
    override val visibility: RsVisibility
        get() = (owner as? RsEnumVariant)?.visibility ?: super.visibility

    @JvmDefault
    override val isPublic: Boolean
        get() = (owner as? RsEnumVariant)?.isPublic ?: super.isPublic
}
