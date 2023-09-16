/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.psi.ext.RsElement

sealed class MirStatement(
    val source: MirSourceInfo,
) {
    class Assign(val place: MirPlace, val rvalue: MirRvalue, source: MirSourceInfo) : MirStatement(source) {
        override fun toString() = "Assign(place=$place, rvalue=$rvalue)"
    }

    class StorageDead(val local: MirLocal, source: MirSourceInfo) : MirStatement(source) {
        override fun toString() = "StorageDead(local=$local)"
    }

    class StorageLive(val local: MirLocal, source: MirSourceInfo) : MirStatement(source) {
        override fun toString() = "StorageLive(local=$local)"
    }

    class FakeRead(val cause: Cause, val place: MirPlace, source: MirSourceInfo) : MirStatement(source) {
        sealed class Cause {
            data class ForMatchedPlace(val element: RsElement?) : Cause()
            data class ForLet(val element: RsElement?) : Cause()
            object ForIndex : Cause()
        }
    }
}
