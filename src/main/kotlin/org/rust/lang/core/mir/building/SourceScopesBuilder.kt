/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.MirSpan
import org.rust.lang.core.mir.schemas.MirSourceScope

class SourceScopesBuilder(span: MirSpan) {
    var sourceScope: MirSourceScope = MirSourceScope(span, null)
    val outermost: MirSourceScope get() = stack.first()

    private val stack = mutableListOf(sourceScope)

    fun newSourceScope(span: MirSpan): MirSourceScope {
        return MirSourceScope(span, sourceScope).also { stack.add(it) }
    }

    fun build(): List<MirSourceScope> = stack
}
