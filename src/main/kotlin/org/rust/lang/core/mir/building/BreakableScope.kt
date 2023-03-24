/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.MirPlace
import org.rust.lang.core.types.regions.Scope

class BreakableScope(
    val scope: Scope,
    val breakDestination: MirPlace,
    val breakDrops: DropTree,
    val continueDrops: DropTree?,
)
