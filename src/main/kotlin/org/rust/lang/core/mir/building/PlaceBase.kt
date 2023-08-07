/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.MirLocal

sealed class PlaceBase {
    data class Local(val local: MirLocal) : PlaceBase()
    // TODO: upvar
}
