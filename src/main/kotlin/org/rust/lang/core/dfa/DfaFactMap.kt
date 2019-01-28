/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import com.intellij.util.keyFMap.KeyFMap
import org.rust.lang.core.dfa.value.DfaValue

class DfaFactMap private constructor(private val myMap: KeyFMap) {

    /**
     * Returns a fact value for given fact type stored in current fact map or
     * null if current fact map is not restricted by given fact type.
     *
     * @param type fact type to fetch
     * @param <T> type of the fact value
     * @return a fact value or null
    </T> */
    operator fun <T> get(type: DfaFactType<T>): T? = myMap.get(type)

    /**
     * Returns a new fact map which is the same as current map, but replaces fact value of given type with the new value.
     *
     * @param type fact type to replace
     * @param value new value (supplying null here effectively removes the value)
     * @param <T> the type of fact value
     * @return a new fact map. May return itself if it's detected that this fact map already contains the supplied value.
    </T> */
    fun <T> with(type: DfaFactType<T>, value: T?): DfaFactMap {
        val newMap = if (value == null || type.isUnknown(value)) myMap.minus(type) else myMap.plus(type, value)
        return if (newMap === myMap) this else DfaFactMap(newMap)
    }

    /**
     * Checks whether the passed fact map is a sub-state of this map (i.e. any exact value
     * which conforms the passed fact map also conforms this fact map).
     *
     * @param subMap a fact map to check
     * @return true if this fact map is a super-state of supplied fact map.
     */
    fun isSuperStateOf(subMap: DfaFactMap): Boolean {
        for (key in DfaFactType.types) {
            val type = key as DfaFactType<Any>
            val thisValue = myMap.get(type)
            val other = subMap[type]
            if (!type.isSuper(thisValue, other)) return false
        }
        return true
    }

    /**
     * Returns a fact map which is additionally restricted by supplied fact.
     * The returned map is a sub-state of this map.
     *
     * @param type  a type of a new fact
     * @param value a fact value which should be true for the resulting map. Passing null
     * is essentially a no-op as no additional restriction is applied.
     * @param <T>   a fact value type
     * @return a new fact map or null if new fact is incompatible with current fact map (no value is possible
     * which conforms to the new fact and to this fact map simultaneously). May return itself if
     * it's known that new fact does not actually change this map.
    </T> */
    fun <T> intersect(type: DfaFactType<T>, value: T?): DfaFactMap? {
        if (value == null || type.isUnknown(value)) return this
        val curFact = get(type) ?: return with(type, value)
        val newFact = type.intersectFacts(curFact, value)
        return if (newFact == null) null else with(type, newFact)
    }

    private fun <TT> intersect(otherMap: DfaFactMap, type: DfaFactType<TT>): DfaFactMap? {
        return intersect(type, otherMap[type])
    }

    fun intersect(other: DfaFactMap): DfaFactMap? {
        var result: DfaFactMap? = this
        for (type in DfaFactType.types) {
            result = result!!.intersect(other, type)
            if (result == null) return null
        }
        return result
    }

    /**
     * Returns a fact map which additionally allows having supplied value for the supplied fact
     *
     * @param type  a type of a new fact
     * @param value an additional fact value which should be allowed. Passing null means that fact may have any value
     * @param <T>   a fact value type
     * @return a new fact map. May return itself if it's known that new fact does not actually change this map.
    </T> */
    fun <T> unite(type: DfaFactType<T>, value: T?): DfaFactMap {
        if (value == null) return with(type, null)
        val curFact = get(type) ?: return this
        val newFact = type.uniteFacts(curFact, value)
        return with(type, newFact)
    }

    private fun <TT> unite(otherMap: DfaFactMap, type: DfaFactType<TT>): DfaFactMap {
        return unite(type, otherMap[type])
    }

    fun unite(other: DfaFactMap): DfaFactMap = DfaFactType.types.fold(this) { map, type -> map.unite(other, type) }

    override fun equals(other: Any?): Boolean = if (this === other) true else other is DfaFactMap && myMap == other.myMap

    override fun hashCode(): Int = myMap.hashCode()

    override fun toString(): String = myMap.keys.joinToString(", ") { f ->
        val key = f as DfaFactType<Any>
        val value = myMap.get(f)!!
        key.toString(value)
    }

    companion object {
        val EMPTY = DfaFactMap(KeyFMap.EMPTY_MAP)

        /**
         * Derives facts which might be known from given DfaValue without knowing the particular memory state
         *
         * @param value a value to derive facts from
         * @return map of facts derived from the value
         */
        fun fromDfaValue(value: DfaValue): DfaFactMap = DfaFactType.types.fold(EMPTY) { map, type -> updateMap(map, type, value) }

        private fun <T> updateMap(map: DfaFactMap, factType: DfaFactType<T>, value: DfaValue): DfaFactMap = map.with(factType, factType.fromDfaValue(value))
    }
}
