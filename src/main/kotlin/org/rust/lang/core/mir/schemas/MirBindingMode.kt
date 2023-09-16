/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.mir.RsBindingModeWrapper
import org.rust.lang.core.types.ty.Mutability

sealed class MirBindingMode {
    data class BindByReference(val mutability: Mutability) : MirBindingMode()
    data class BindByValue(val mutability: Mutability) : MirBindingMode()

    companion object {
        fun from(rsBindingMode: RsBindingModeWrapper): MirBindingMode {
            return if (rsBindingMode.ref == null) {
                BindByValue(rsBindingMode.mutability)
            } else {
                BindByReference(rsBindingMode.mutability)
            }
        }
    }
}
