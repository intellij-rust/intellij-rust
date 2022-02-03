/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsConstParameter
import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.RsTypeParameterList

fun RsTypeParameterList.getGenericParameters(
    includeLifetimes: Boolean = true,
    includeTypes: Boolean = true,
    includeConsts: Boolean = true
): List<RsGenericParameter> = stubChildrenOfType<RsGenericParameter>().filter {
    when (it) {
        is RsLifetimeParameter -> includeLifetimes
        is RsTypeParameter -> includeTypes
        is RsConstParameter -> includeConsts
        else -> false
    }
}
