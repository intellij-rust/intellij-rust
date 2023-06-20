/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.types.ty.Ty

data class MirVarDebugInfo(
    val name: String,
    val source: MirSourceInfo,
    val contents: Contents,
    /**
     * When present, indicates what argument number this variable is in the function that it
     * originated from (starting from 1).
     */
    val argumentIndex: Int? = null,
) {
    sealed class Contents {
        data class Place(val place: MirPlace) : Contents()
        data class Constant(val constant: MirConstant) : Contents()
        data class Composite(val ty: Ty, val fragments: List<Fragment>) : Contents()
    }

    data class Fragment(val projection: List<PlaceElem>, val contents: MirPlace)
}
