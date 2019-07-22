/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * A very simple interface for caching
 */
interface Cache<in K: Any, V: Any> {
    fun getOrPut(key: K, defaultValue: () -> V): V

    companion object {
        fun <K: Any, V: Any> new(): Cache<K, V> =
            fromMutableMap(mutableMapOf())

        fun <K: Any, V: Any> newConcurrent(): Cache<K, V> =
            fromConcurrentMap(ConcurrentHashMap())

        private fun <K: Any, V: Any> fromMutableMap(map: MutableMap<K, V>): Cache<K, V> {
            return object : Cache<K, V> {
                override fun getOrPut(key: K, defaultValue: () -> V): V =
                    map.getOrPut(key, defaultValue)
            }
        }

        fun <K: Any, V: Any> fromConcurrentMap(map: ConcurrentMap<K, V>): Cache<K, V> {
            return object : Cache<K, V> {
                override fun getOrPut(key: K, defaultValue: () -> V): V =
                    map.getOrPut(key, defaultValue)
            }
        }
    }
}
