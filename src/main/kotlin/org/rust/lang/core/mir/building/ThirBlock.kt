/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.MirSourceInfo

data class ThirBlock(
    // TODO: statements and much mo
    val expr: ThirExpr,
    val source: MirSourceInfo,
)
