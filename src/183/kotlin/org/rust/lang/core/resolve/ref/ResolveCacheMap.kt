/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import java.lang.ref.ReferenceQueue

class ResolveCacheMap<K, V> : ResolveCacheMapBase<K, V>() {
    @Suppress("UNCHECKED_CAST")
    override fun createValueReference(value: V, queue: ReferenceQueue<in V>): ValueReference<K, V> =
        createValueReferenceInner(value, queue as ReferenceQueue<V>)
}
