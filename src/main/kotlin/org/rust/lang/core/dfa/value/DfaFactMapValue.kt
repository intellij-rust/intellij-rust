/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa.value

import org.rust.lang.core.dfa.DfaFactMap
import org.rust.lang.core.dfa.DfaFactType
import org.rust.lang.core.types.ty.Ty

class DfaFactMapValue(factory: DfaValueFactory, private val facts: DfaFactMap) : DfaValue(factory) {
    fun <T> withFact(factType: DfaFactType<T>, value: T?): DfaValue = factory.factFactory.createValue(facts.with(factType, value))

    operator fun <T> get(factType: DfaFactType<T>): T? = facts[factType]

    override val type: Ty? get() = get(DfaFactType.RANGE)?.type
    override val minus: DfaValue get() = factory.factFactory.createValue(facts.with(DfaFactType.RANGE, get(DfaFactType.RANGE)?.unaryMinus()))
    override val invert: DfaValue get() = factory.factFactory.createValue(facts.with(DfaFactType.RANGE, get(DfaFactType.RANGE)?.invert))
    override val isUnknown: Boolean get() = get(DfaFactType.RANGE)?.isUnknown ?: false
    override fun toString(): String = facts.toString()
    override val isEmpty: Boolean get() = get(DfaFactType.RANGE)?.isEmpty ?: false
}

class DfaFactMapFactory(val factory: DfaValueFactory) {
    private val values = hashMapOf<DfaFactMap, DfaFactMapValue>()
    fun <T> createValue(factType: DfaFactType<T>, value: T?): DfaValue {
//        if (factType === DfaFactType.RANGE && value is LongRangeSet) {
//            if (!value.isEmpty && value.min == value.max) {
//                return factory.constFactory.createFromValue(value.min, value.type)
//            }
//        }
        return createValue(DfaFactMap.EMPTY.with(factType, value))
    }

    fun createValue(facts: DfaFactMap): DfaValue = if (facts === DfaFactMap.EMPTY) DfaUnknownValue else values.computeIfAbsent(facts) { f -> DfaFactMapValue(factory, f) }
}
