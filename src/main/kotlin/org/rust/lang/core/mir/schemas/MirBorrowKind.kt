/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

val MirBorrowKind.allowTwoPhaseBorrow: Boolean
    get() = if (this is MirBorrowKind.Mut) allowTwoPhaseBorrow else false

sealed class MirBorrowKind {
    object Shared : MirBorrowKind()
    object Shallow : MirBorrowKind()
    object Unique : MirBorrowKind()
    data class Mut(val allowTwoPhaseBorrow: Boolean) : MirBorrowKind()
}
