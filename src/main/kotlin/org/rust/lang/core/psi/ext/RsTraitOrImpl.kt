/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsMembers
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.types.BoundElement

interface RsTraitOrImpl : RsItemElement, RsGenericDeclaration {
    val members: RsMembers?

    val implementedTrait: BoundElement<RsTraitItem>?

    val associatedTypesTransitively: Collection<RsTypeAlias>
}

val RsTraitOrImpl.expandedMembers: List<RsAbstractable>
    get() = members?.expandedMembers.orEmpty()
