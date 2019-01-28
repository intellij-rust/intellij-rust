/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa.value

import org.rust.lang.core.dfa.DfaFactMap
import org.rust.lang.core.types.ty.Ty

abstract class DfaValue(valueFactory: DfaValueFactory?) {
    val id: Int = valueFactory?.registerValue(this) ?: 0

    val factory: DfaValueFactory  by lazy { valueFactory!! }

    open val type: Ty? = null

    open val negated: DfaValue = DfaUnknownValue

    open val minus: DfaValue = DfaUnknownValue

    open val invert: DfaValue = DfaUnknownValue

    open val isUnknown: Boolean get() = this is DfaUnknownValue

    open val isEmpty: Boolean get() = false

    override fun equals(other: Any?): Boolean = other is DfaValue && id == other.id

    override fun hashCode(): Int = id

    open fun unite(other: DfaValue): DfaValue {
        if (this == other) return this
        if (this.isUnknown || other.isUnknown) return DfaUnknownValue
        return factory.factFactory.createValue(DfaFactMap.fromDfaValue(this).unite(DfaFactMap.fromDfaValue(other)))
    }
}

