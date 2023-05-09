/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

sealed class MirProjectionElem<out T> {
    object Deref : MirProjectionElem<Nothing>()
    data class Field<T>(val fieldIndex: Int, val elem: T) : MirProjectionElem<T>()
}
