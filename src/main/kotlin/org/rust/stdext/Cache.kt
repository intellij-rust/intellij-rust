/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

/**
 * A very simple interface for caching
 */
interface Cache<in K : Any, V : Any> {
    fun getOrPut(key: K, defaultValue: () -> V): V

    companion object {
        fun <K : Any, V : Any> new(): Cache<K, V> =
            fromMutableMap(mutableMapOf())

        private fun <K : Any, V : Any> fromMutableMap(map: MutableMap<K, V>): Cache<K, V> {
            return object : Cache<K, V> {
                override fun getOrPut(key: K, defaultValue: () -> V): V =
                    map.getOrPut(key, defaultValue)
            }
        }
    }
}
