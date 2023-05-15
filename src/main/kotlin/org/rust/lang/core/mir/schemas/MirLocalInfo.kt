/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

sealed class MirLocalInfo {
    data class User(val form: MirClearCrossCrate<MirBindingForm>) : MirLocalInfo()
    data class StaticRef(val isThreadLocal: Boolean) : MirLocalInfo()
}
