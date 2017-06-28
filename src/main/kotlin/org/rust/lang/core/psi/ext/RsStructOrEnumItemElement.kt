/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsTraitItem

interface RsStructOrEnumItemElement : RsQualifiedNamedElement, RsTypeBearingItemElement, RsGenericDeclaration

val RsStructOrEnumItemElement.derivedTraits: List<RsTraitItem>
    get() = queryAttributes
        .deriveAttribute
        ?.metaItemArgs
        ?.metaItemList
        ?.mapNotNull { it.reference.resolve() as? RsTraitItem }
        ?: emptyList()
