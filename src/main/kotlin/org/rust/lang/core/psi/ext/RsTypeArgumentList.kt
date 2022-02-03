/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*

val RsTypeArgumentList.lifetimeArguments: List<RsLifetime> get() = lifetimeList

val RsTypeArgumentList.typeArguments: List<RsTypeReference>
    get() = typeReferenceList.filter { ref ->
        val type = ref as? RsBaseType
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
