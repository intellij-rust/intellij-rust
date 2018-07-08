/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.types.HAS_RE_EARLY_BOUND_MASK

/**
 * Region bound in a type or fn declaration which will be
 * substituted 'early' -- that is, at the same time when type
 * parameters are substituted.
 */
data class ReEarlyBound(val parameter: RsLifetimeParameter) : Region(HAS_RE_EARLY_BOUND_MASK) {
    override fun toString(): String = parameter.name ?: "<unknown>"
}
