/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.openapi.util.Condition
import com.intellij.util.*

// Be careful with queries: they are `Iterable`s, so they have Kotlin's
// `map`, `filter` and friends, which convert then to List.

fun <U> Query<U>.filterQuery(condition: Condition<U>): Query<U> = FilteredQuery(this, condition)

inline fun <reified V: Any> Query<*>.filterIsInstanceQuery(): Query<V> = InstanceofQuery(this, V::class.java)

fun <U, V> Query<U>.mapQuery(f: (U) -> V) = object : AbstractQuery<V>() {
    override fun processResults(consumer: Processor<V>): Boolean {
        return this@mapQuery.forEach(Processor<U> { t -> consumer.process(f(t)) })
    }
}
