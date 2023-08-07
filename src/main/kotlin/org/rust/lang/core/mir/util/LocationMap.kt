/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.util

import com.intellij.util.containers.map2Array
import org.rust.lang.core.mir.schemas.MirBody
import org.rust.lang.core.mir.schemas.MirLocation

class LocationMap<V: Any>(
    body: MirBody
) : MutableMap<MirLocation, V> {
    private val inner: Array<Array<Any?>> = body.basicBlocks.map2Array {
        arrayOfNulls(it.statements.size + 1)
    }

    override val entries: MutableSet<MutableMap.MutableEntry<MirLocation, V>>
        get() = throw UnsupportedOperationException()

    override val keys: MutableSet<MirLocation>
        get() = throw UnsupportedOperationException()

    override val size: Int
        get() = throw UnsupportedOperationException()

    override val values: MutableCollection<V>
        get() = throw UnsupportedOperationException()

    override fun clear() {
        throw UnsupportedOperationException()
    }

    override fun isEmpty(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun remove(key: MirLocation): V? {
        throw UnsupportedOperationException()
    }

    override fun putAll(from: Map<out MirLocation, V>) {
        throw UnsupportedOperationException()
    }

    override fun put(key: MirLocation, value: V): V? {
        val perStmt = inner[key.block.index]
        val oldValue = perStmt[key.statementIndex]
        perStmt[key.statementIndex] = value
        @Suppress("UNCHECKED_CAST")
        return oldValue as V?
    }

    override fun get(key: MirLocation): V? {
        @Suppress("UNCHECKED_CAST")
        return inner[key.block.index][key.statementIndex] as V?
    }

    override fun containsValue(value: V): Boolean {
        throw UnsupportedOperationException()
    }

    override fun containsKey(key: MirLocation): Boolean = get(key) != null
}
