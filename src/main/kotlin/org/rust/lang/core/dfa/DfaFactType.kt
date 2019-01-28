/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import com.intellij.openapi.util.Key
import org.rust.lang.core.dfa.value.DfaFactMapValue
import org.rust.lang.core.dfa.value.DfaValue

abstract class DfaFactType<T> constructor(val name: String) : Key<T>("DfaFactType: $name") {
    open fun fromDfaValue(value: DfaValue): T? = if (value is DfaFactMapValue) value[this] else null

    open fun isSuper(superFact: T?, subFact: T?): Boolean = superFact == subFact

    open fun isUnknown(fact: T): Boolean = false

    /**
     * Intersects two facts of this type.
     *
     * @param left left fact
     * @param right right fact
     * @return intersection fact or null if facts are incompatible
     */
    open fun intersectFacts(left: T, right: T): T? = if (left == right) left else null

    /**
     * Unites two facts of this type.
     *
     * @param left left fact
     * @param right right fact
     * @return unite fact (null means that the fact can have any value)
     */
    open fun uniteFacts(left: T, right: T): T? = if (left == right) left else null

    /**
     * Produces a short suitable for debug output fact representation
     * @param fact a fact to represent
     * @return a string representation of the fact
     */
    open fun toString(fact: T): String = fact.toString()

    companion object {
        /**
         * This fact is applied to the integral values (of types i8, u8, i16, etc.).
         * Its value represents a range of possible values.
         */
        val RANGE: DfaFactType<LongRangeSet> = object : DfaFactType<LongRangeSet>("Range") {
            override fun isSuper(superFact: LongRangeSet?, subFact: LongRangeSet?): Boolean =
                superFact == null || subFact != null && superFact.contains(subFact)

            override fun isUnknown(fact: LongRangeSet): Boolean = fact.isUnknown

            override fun fromDfaValue(value: DfaValue): LongRangeSet? = LongRangeSet.fromDfaValue(value)

            override fun uniteFacts(left: LongRangeSet, right: LongRangeSet): LongRangeSet? = left.unite(right)

            override fun intersectFacts(left: LongRangeSet, right: LongRangeSet): LongRangeSet? {
                val intersection = left.intersect(right)
                return if (intersection.isEmpty) null else intersection
            }
        }

        val types: List<DfaFactType<*>> = listOf(RANGE)
    }
}
