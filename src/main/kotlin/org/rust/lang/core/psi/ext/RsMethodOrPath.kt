/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsAssocTypeBinding
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.RsTypeArgumentList
import org.rust.lang.core.psi.RsTypeReference

val RsMethodOrPath.lifetimeArguments: List<RsLifetime> get() = typeArgumentList?.lifetimeArguments.orEmpty()
val RsMethodOrPath.typeArguments: List<RsTypeReference> get() = typeArgumentList?.typeArguments.orEmpty()
val RsMethodOrPath.constArguments: List<RsElement> get() = typeArgumentList?.constArguments.orEmpty()
val RsMethodOrPath.assocTypeBindings: List<RsAssocTypeBinding> get() = typeArgumentList?.assocTypeBindingList.orEmpty()

fun RsMethodOrPath.getGenericArguments(
    includeLifetimes: Boolean = true,
    includeTypes: Boolean = true,
    includeConsts: Boolean = true,
    includeAssocBindings: Boolean = true
): List<RsElement> = typeArgumentList?.getGenericArguments(
    includeLifetimes,
    includeTypes,
    includeConsts,
    includeAssocBindings
).orEmpty()

interface RsMethodOrPath : RsElement {
    val typeArgumentList: RsTypeArgumentList?
}
