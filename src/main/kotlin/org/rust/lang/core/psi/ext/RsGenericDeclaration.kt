/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*

interface RsGenericDeclaration : RsElement {
    val typeParameterList: RsTypeParameterList?
    val whereClause: RsWhereClause?
}

val RsGenericDeclaration.genericParameters: List<RsGenericParameter>
    get() = typeParameterList?.genericParameterList.orEmpty()

val RsGenericDeclaration.typeParameters: List<RsTypeParameter>
    get() = typeParameterList?.typeParameterList.orEmpty()

val RsGenericDeclaration.lifetimeParameters: List<RsLifetimeParameter>
    get() = typeParameterList?.lifetimeParameterList.orEmpty()

val RsGenericDeclaration.constParameters: List<RsConstParameter>
    get() = typeParameterList?.constParameterList.orEmpty()

val RsGenericDeclaration.requiredGenericParameters: List<RsTypeParameter>
    get() = typeParameters.filter { it.typeReference == null }
