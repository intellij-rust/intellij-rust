/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.psi.ext.RsElement

sealed class MirSpan {
    open val end: MirSpan get() = End(reference)
    open val endPoint: MirSpan get() = EndPoint(reference)
    abstract val reference: RsElement

    class Full(override val reference: RsElement) : MirSpan()

    class Start(override val reference: RsElement) : MirSpan()

    class EndPoint(override val reference: RsElement) : MirSpan() {
        override val endPoint get() = this
    }

    class End(override val reference: RsElement) : MirSpan() {
        override val end: MirSpan get() = this
    }

    object Fake : MirSpan() {
        // end of fake source is still a fake source sound ok
        override val end: MirSpan get() = this
        override val endPoint get() = this
        override val reference: RsElement get() = error("Fake span have no reference")
    }

    companion object {
        operator fun invoke(reference: RsElement) = Full(reference)
    }
}

