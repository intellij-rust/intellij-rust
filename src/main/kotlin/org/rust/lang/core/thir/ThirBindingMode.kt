/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.mir.schemas.MirBorrowKind

sealed class ThirBindingMode {
    object ByValue : ThirBindingMode()
    data class ByRef(val kind: MirBorrowKind) : ThirBindingMode()
}
