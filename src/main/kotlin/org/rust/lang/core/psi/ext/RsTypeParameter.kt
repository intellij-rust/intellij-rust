/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsPolybound
import org.rust.lang.core.psi.RsTypeParameter

val RsTypeParameter.bounds: List<RsPolybound> get() {
    val owner = parent?.parent as? RsGenericDeclaration
    val whereBounds =
        owner?.whereClause?.wherePredList.orEmpty()
            .filter { (it.typeReference?.typeElement as? RsBaseType)?.path?.reference?.resolve() == this }
            .flatMap { it.typeParamBounds?.polyboundList.orEmpty() }

    return typeParamBounds?.polyboundList.orEmpty() + whereBounds
}
