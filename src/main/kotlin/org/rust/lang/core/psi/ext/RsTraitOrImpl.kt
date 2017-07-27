/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*
import org.rust.lang.core.types.BoundElement

interface RsTraitOrImpl : RsItemElement, RsInnerAttributeOwner, RsGenericDeclaration {
    val members: RsMembers?

    val implementedTrait: BoundElement<RsTraitItem>?

    val associatedTypesTransitively: Collection<RsTypeAlias>
}

val BoundElement<RsTraitOrImpl>.functionsWithInherited: List<BoundElement<RsFunction>> get() {
    val directlyImplemented = element.members?.functionList.orEmpty().map { BoundElement(it, subst) }
    val inherited = when (element) {
        is RsImplItem -> {
            val trait = element.implementedTrait
            if (trait == null) emptyList() else {
                val direct = directlyImplemented.map { it.element.name }.toSet()
                trait.element.members?.functionList.orEmpty()
                    .filter { it.name !in direct }
                    .map { BoundElement(it, subst) }
            }
        }
        else -> emptyList()
    }
    return directlyImplemented + inherited
}
