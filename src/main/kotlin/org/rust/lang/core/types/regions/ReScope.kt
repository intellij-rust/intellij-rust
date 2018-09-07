/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import org.rust.lang.core.types.HAS_FREE_REGIONS_MASK
import org.rust.lang.core.types.TypeFlags

/**
 * A concrete region naming some statically determined scope (e.g. an expression or sequence of statements) within the
 * current function.
 */
data class ReScope(val scope: Scope) : Region() {
    override val flags: TypeFlags = HAS_FREE_REGIONS_MASK
}
