/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import org.rust.lang.core.types.HAS_FREE_REGIONS_MASK
import org.rust.lang.core.types.TypeFlags

/**
 * Empty region is for data that is never accessed. Bottom in the region lattice.
 * The only way to get an instance of [ReEmpty] is to have a region variable with no constraints.
 */
object ReEmpty : Region() {
    override val flags: TypeFlags = HAS_FREE_REGIONS_MASK
}
