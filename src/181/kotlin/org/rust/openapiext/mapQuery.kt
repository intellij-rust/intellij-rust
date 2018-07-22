/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.util.AbstractQuery
import com.intellij.util.Processor
import com.intellij.util.Query

fun <U, V> Query<U>.mapQuery(f: (U) -> V) = object : AbstractQuery<V>() {
    override fun processResults(consumer: Processor<V>): Boolean {
        return this@mapQuery.forEach(Processor<U> { t -> consumer.process(f(t)) })
    }
}
