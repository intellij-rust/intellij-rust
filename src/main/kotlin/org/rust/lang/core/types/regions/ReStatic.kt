/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import org.rust.lang.core.types.HAS_FREE_REGIONS_MASK
import org.rust.lang.core.types.TypeFlags

/** Static data that has an "infinite" lifetime. Top in the region lattice. */
object ReStatic : Region() {
    override val flags: TypeFlags = HAS_FREE_REGIONS_MASK

    override fun toString(): String = "'static"
}
