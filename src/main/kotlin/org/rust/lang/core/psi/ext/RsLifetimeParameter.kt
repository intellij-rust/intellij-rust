/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.RsLifetimeParameter

val RsLifetimeParameter.bounds: List<RsLifetime> get() {
    val owner = parent?.parent as? RsGenericDeclaration
    val whereBounds =
        owner?.whereClause?.wherePredList.orEmpty()
            .filter { it.lifetime?.reference?.resolve() == this }
            .flatMap { it.lifetimeParamBounds?.lifetimeList.orEmpty() }
    return lifetimeParamBounds?.lifetimeList.orEmpty() + whereBounds
}
