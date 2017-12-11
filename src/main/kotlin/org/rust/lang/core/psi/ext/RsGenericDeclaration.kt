/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.RsTypeParameterList
import org.rust.lang.core.psi.RsWhereClause

interface RsGenericDeclaration : RsElement {
    val typeParameterList: RsTypeParameterList?
    val whereClause: RsWhereClause?
}

val RsGenericDeclaration.typeParameters: List<RsTypeParameter>
    get() = typeParameterList?.typeParameterList.orEmpty()


val RsGenericDeclaration.lifetimeParameters: List<RsLifetimeParameter>
    get() = typeParameterList?.lifetimeParameterList.orEmpty()
