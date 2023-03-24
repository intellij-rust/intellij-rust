/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.mir.RsBindingModeWrapper
import org.rust.lang.core.psi.RsBindingMode

sealed class ThirBindingMode {
    abstract val rs: RsBindingModeWrapper

    data class ByValue(override val rs: RsBindingModeWrapper) : ThirBindingMode()
}
