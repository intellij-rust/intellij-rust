/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.psi.ext.RsElement

sealed class MirSourceInfo {
    abstract val end: MirSourceInfo

    class Full(val reference: RsElement) : MirSourceInfo() {
        override val end get() = End(reference)
    }

    class End(val reference: RsElement) : MirSourceInfo() {
        override val end get() = this
    }

    object Fake : MirSourceInfo() {
        override val end get() = this // end of fake source is still a fake source sound ok
    }

    companion object {
        operator fun invoke(reference: RsElement) = Full(reference)
    }
}

