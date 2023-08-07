/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.MirLocal
import org.rust.lang.core.mir.schemas.MirSourceInfo

class Drop(
    val local: MirLocal,
    val kind: Kind,
    val source: MirSourceInfo,
) {
    enum class Kind {
        VALUE,
        STORAGE
    }

    override fun toString(): String {
        return "Drop(local=$local, kind=$kind, source=$source)" // TODO
    }

    companion object {
        val fake = Drop(MirLocal.fake, Kind.STORAGE, MirSourceInfo.fake)
    }
}
