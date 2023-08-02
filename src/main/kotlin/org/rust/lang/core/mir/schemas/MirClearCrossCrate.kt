/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

sealed class MirClearCrossCrate<out T> {
    object Clear : MirClearCrossCrate<Nothing>()
    data class Set<T>(val value: T) : MirClearCrossCrate<T>()
}
