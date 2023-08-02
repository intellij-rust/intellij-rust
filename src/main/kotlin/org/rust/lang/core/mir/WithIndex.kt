/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

interface WithIndex {
    val index: Int
}

inline fun <T: WithIndex> MutableList<T>.allocate(constructor: (index: Int) -> T): T {
    val index = size
    val value = constructor(index)
    add(value)
    return value
}

operator fun <T> List<T>.get(key: WithIndex) = this[key.index]
