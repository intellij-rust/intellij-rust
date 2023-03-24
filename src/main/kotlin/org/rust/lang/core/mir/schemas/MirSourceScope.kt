/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

data class MirSourceScope(
    val span: MirSpan,
    val parentScope: MirSourceScope?,
) {
    companion object {
        val fake = MirSourceScope(MirSpan.Fake, null)
    }
}
