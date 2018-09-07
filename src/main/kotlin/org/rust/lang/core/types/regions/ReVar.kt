/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import org.rust.lang.core.types.HAS_FREE_REGIONS_MASK
import org.rust.lang.core.types.HAS_RE_INFER_MASK
import org.rust.lang.core.types.TypeFlags
import org.rust.lang.core.types.infer.Node
import org.rust.lang.core.types.infer.NodeOrValue
import org.rust.lang.core.types.infer.VarValue

/** A region variable. Should not exist after type inference. */
class ReVar(
    val index: Int,
    override var parent: NodeOrValue = VarValue(null, 0)
) : Region(), Node {
    override val flags: TypeFlags = HAS_FREE_REGIONS_MASK or HAS_RE_INFER_MASK

    override fun toString(): String = "'_"
}
