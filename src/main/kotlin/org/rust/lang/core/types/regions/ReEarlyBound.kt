/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.types.HAS_RE_EARLY_BOUND_MASK
import org.rust.lang.core.types.TypeFlags

/**
 * Region bound in a type or fn declaration, which will be substituted 'early' -- that is,
 * at the same time when type parameters are substituted.
 */
class ReEarlyBound(parameter: RsLifetimeParameter) : Region() {
    val parameter: RsLifetimeParameter = CompletionUtil.getOriginalOrSelf(parameter)

    override val flags: TypeFlags = HAS_RE_EARLY_BOUND_MASK

    override fun equals(other: Any?): Boolean = other is ReEarlyBound && other.parameter == parameter

    override fun hashCode(): Int = parameter.hashCode()

    override fun toString(): String = parameter.name ?: "<unknown>"
}
