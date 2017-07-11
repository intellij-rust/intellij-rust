/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("unused")

package org.rust.utils

import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureNanoTime

/**
 * Useful to quickly measure total times of a certain repeated
 * operation during profiling.
 *
 * Create a global StopWatch instance, use `measure` function
 * around interesting block, see the results at the end.
 *
 * **FOR DEVELOPMENT ONLY**
 */
class RsStopWatch(
    private val name: String,
    private var totalNs: AtomicLong = AtomicLong(0)
) {
    init {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                println("${this@RsStopWatch.name}: ${totalNs.get() / 1_000_000} ms")
            }
        })
    }

    fun <T> measure(block: () -> T): T {
        var result: T? = null
        totalNs.addAndGet(measureNanoTime { result = block() })
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
