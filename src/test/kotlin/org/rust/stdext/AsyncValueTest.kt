/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import junit.framework.TestCase
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveTask
import java.util.concurrent.TimeUnit


class AsyncValueTest : TestCase() {
    fun `test async value is thread-safe`() = repeat(32) {
        val value = AsyncValue(0)
        val pool = ForkJoinPool(16)
        val n = 16
        val m = 16
        for (i in 0 until n) {
            pool.execute {
                for (j in 0 until m) {
                    value.updateAsync { v ->
                        val p = CompletableFuture<Int>()
                        object : RecursiveTask<Unit>() {
                            override fun compute() {
                                p.complete(v + 1)
                            }
                        }.fork()
                        p
                    }
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.MINUTES)
        val result = value.updateSync { v -> v }.get(1, TimeUnit.MINUTES)
        check(value.currentState == n * m)
        check(result == n * m) {
            "Impossible: expected ${n * m}, got $result"
        }
    }
}
