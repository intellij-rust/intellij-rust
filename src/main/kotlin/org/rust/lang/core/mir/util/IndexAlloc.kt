/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.util

import org.rust.lang.core.mir.WithIndex

class IndexAlloc<T: WithIndex> {
    private var counter: Int = 0

    val size: Int get() = counter

    fun allocate(constructor: (index: Int) -> T): T {
        return constructor(counter++)
    }
}
