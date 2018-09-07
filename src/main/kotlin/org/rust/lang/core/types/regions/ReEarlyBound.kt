/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.psi.ext.LifetimeName
import org.rust.lang.core.psi.ext.isStatic
import org.rust.lang.core.types.HAS_FREE_REGIONS_MASK
import org.rust.lang.core.types.HAS_RE_EARLY_BOUND_MASK
import org.rust.lang.core.types.TypeFlags

/**
 * Region bound in a type or fn declaration which will be substituted 'early' -- that is, at the same time when type
 * parameters are substituted.
 */
data class ReEarlyBound(val origin: PsiElement, val name: LifetimeName) : Region() {
    override val flags: TypeFlags = HAS_FREE_REGIONS_MASK or HAS_RE_EARLY_BOUND_MASK

    init {
        require(!name.isStatic)
    }

    override fun toString(): String =
        when (name) {
            is LifetimeName.Parameter -> name.name
            LifetimeName.Implicit -> ""
            LifetimeName.Underscore -> "'_"
            else -> error("impossible")
        }

    companion object {

        fun named(parameter: RsLifetimeParameter): ReEarlyBound {
            val name = LifetimeName.Parameter(parameter.quoteIdentifier.text)
            return ReEarlyBound(parameter, name)
        }

        fun implicit(parent: PsiElement): ReEarlyBound =
            ReEarlyBound(parent, LifetimeName.Implicit)
    }
}
