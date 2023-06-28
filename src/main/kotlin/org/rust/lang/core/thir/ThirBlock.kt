/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.mir.schemas.MirSpan
import org.rust.lang.core.types.regions.Scope

data class ThirBlock(
    // TODO: statements and much more
    val regionScope: Scope,
    val destructionScope: Scope?,
    val statements: List<ThirStatement>,
    val expr: ThirExpr?,
    val source: MirSpan,
)
