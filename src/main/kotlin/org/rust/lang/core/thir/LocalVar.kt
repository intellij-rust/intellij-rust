/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsSelfParameter

sealed class LocalVar {
    abstract val name: String // just for better internal errors handling

    data class FromPatBinding(val pat: RsPatBinding) : LocalVar() {
        override val name: String get() = pat.name!!
    }

    data class FromSelfParameter(val self: RsSelfParameter) : LocalVar() {
        override val name: String get() = "self"
    }

    companion object {
        operator fun invoke(pat: RsPatBinding) = FromPatBinding(pat)
        operator fun invoke(self: RsSelfParameter) = FromSelfParameter(self)
    }
}
