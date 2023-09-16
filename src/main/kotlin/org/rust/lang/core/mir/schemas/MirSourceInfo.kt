/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

data class MirSourceInfo(
    val span: MirSpan,
    val scope: MirSourceScope,
) {
    companion object {
        val fake = MirSourceInfo(MirSpan.Fake, MirSourceScope.fake)
    }
}
