/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*

fun RsTypeArgumentList.getGenericArguments(
    includeLifetimes: Boolean = true,
    includeTypes: Boolean = true,
    includeConsts: Boolean = true,
    includeAssocBindings: Boolean = true
): List<RsElement> {
    val typeArguments = typeArguments
    return stubChildrenOfType<RsElement>().filter {
        when {
            it is RsLifetime -> includeLifetimes
            it is RsTypeReference && it in typeArguments -> includeTypes
            it is RsExpr || it is RsTypeReference -> includeConsts
            it is RsAssocTypeBinding -> includeAssocBindings
            else -> false
        }
    }
}

val RsTypeArgumentList.lifetimeArguments: List<RsLifetime> get() = lifetimeList

val RsTypeArgumentList.typeArguments: List<RsTypeReference>
    get() = typeReferenceList.filter { ref ->
        val type = ref as? RsPathType
        val element = type?.path?.reference?.resolve()
        element !is RsConstant && element !is RsFunction && element !is RsConstParameter
    }

val RsTypeArgumentList.constArguments: List<RsElement>
    get() {
        val typeArguments = typeArguments
        return stubChildrenOfType<RsElement>().filter {
            it is RsExpr || it is RsTypeReference && it !in typeArguments
        }
    }
