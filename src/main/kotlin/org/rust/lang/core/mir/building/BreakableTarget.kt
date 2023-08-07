/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.types.regions.Scope

sealed class BreakableTarget {
    data class Break(val scope: Scope) : BreakableTarget()
}
