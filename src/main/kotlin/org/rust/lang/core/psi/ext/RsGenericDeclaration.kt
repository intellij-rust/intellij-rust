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

fun RsGenericDeclaration.getGenericParameters(
    includeLifetimes: Boolean = true,
    includeTypes: Boolean = true,
    includeConsts: Boolean = true
): List<RsGenericParameter> = typeParameterList?.getGenericParameters(
    includeLifetimes,
    includeTypes,
    includeConsts
).orEmpty()

val RsGenericDeclaration.typeParameters: List<RsTypeParameter>
    get() = typeParameterList?.typeParameterList.orEmpty()

val RsGenericDeclaration.lifetimeParameters: List<RsLifetimeParameter>
    get() = typeParameterList?.lifetimeParameterList.orEmpty()

val RsGenericDeclaration.constParameters: List<RsConstParameter>
    get() = typeParameterList?.constParameterList.orEmpty()

val RsGenericDeclaration.requiredGenericParameters: List<RsGenericParameter>
    get() = getGenericParameters().filter {
        when (it) {
            is RsTypeParameter -> it.typeReference == null
            is RsConstParameter -> it.expr == null
            else -> false
        }
    }
