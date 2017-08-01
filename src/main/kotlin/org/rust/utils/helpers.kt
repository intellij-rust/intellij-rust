/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.utils


fun <T> buildList(builder: (ListBuilder<T>).() -> Unit): List<T> {
    val result = mutableListOf<T>()
    object : ListBuilder<T> {
        override fun add(item: T) {
            result.add(item)
        }

        override fun addAll(items: List<T>) {
            result.addAll(items)
        }
    }.builder()
    return result
}

interface ListBuilder<in T> {
    fun add(item: T)
    fun addAll(items: List<T>)
}
