/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.mir.WithIndex

data class MirSourceScope(
    override val index: Int,
    val span: MirSpan,
    val parentScope: MirSourceScope?,
) : WithIndex {
    companion object {
        val fake = MirSourceScope(-1, MirSpan.Fake, null)
    }
}
