/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("unused")

package org.rust.stdext

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

class Timings(
    private val valuesTotal: LinkedHashMap<String, Long> = LinkedHashMap(),
    private val invokes: MutableMap<String, Long> = mutableMapOf()
) {
    fun <T> measure(name: String, f: () -> T): T {
        check(name !in valuesTotal)
        return measureInternal(name, f)
    }

    fun <T> measureAverage(name: String, f: () -> T): T = measureInternal(name, f)

    fun merge(other: Timings): Timings {
        val values = values()
        val otherValues = other.values()
        check(values.isEmpty() || otherValues.isEmpty() || values.size == otherValues.size)
        val result = Timings()
        for (k in values.keys.union(otherValues.keys)) {
            result.valuesTotal[k] =
                // https://www.youtube.com/watch?v=vrfYLlR8X8k&feature=youtu.be&t=25m17s
                minOf(values.getOrDefault(k, Long.MAX_VALUE), otherValues.getOrDefault(k, Long.MAX_VALUE))
            result.invokes[k] = 1
        }
        return result
    }

    fun report() {
        val values = values()
        if (values.isEmpty()) {
            println("No metrics recorder")
            return
        }

        val width = values.keys.map { it.length }.max()!!
        for ((k, v) in values) {
            println("${k.padEnd(width)}: $v ms")
        }
        val total = values.values.sum()
        println("$total ms total.")
        println()
    }

    private fun <T> measureInternal(name: String, f: () -> T): T {
        var result: T? = null
        val time = measureTimeMillis { result = f() }
        valuesTotal.merge(name, time, Long::plus)
        invokes.merge(name, 1, Long::plus)
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun values(): Map<String, Long> {
        val result = LinkedHashMap<String, Long>()
        for ((k, sum) in valuesTotal) {
            result[k] = (sum.toDouble() / invokes[k]!!).toLong()
        }
        return result
    }
}


interface RsWatch {
    val name: String
    val totalNs: AtomicLong
}

/**
 * Useful to quickly measure total times of a certain repeated
 * operation during profiling.
 *
 * Create a global StopWatch instance, use [messages] function
 * around interesting block, see the results at the end. Note
 * that [measure] is not reentrant, and will double count
 * recursive activities like resolve. If you want reentrancy,
 * use [RsReentrantStopWatch]
 *
 * **FOR DEVELOPMENT ONLY**
 */
class RsStopWatch(
    override val name: String
) : RsWatch {
    override var totalNs: AtomicLong = AtomicLong(0)

    init {
        WATCHES += this
    }

    fun <T> measure(block: () -> T): T {
        var result: T? = null
        totalNs.addAndGet(measureNanoTime { result = block() })
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

/**
 * Like [RsStopWatch], but requires an explicit start and is reentrant
 */
class RsReentrantStopWatch(override val name: String) : RsWatch {
    override val totalNs: AtomicLong = AtomicLong(0)
    private val started: AtomicBoolean = AtomicBoolean(false)
    private val nesting = NestingCounter()

    init {
        WATCHES += this
    }

    fun start() {
        started.set(true)
    }

    fun <T> measure(block: () -> T): T {
        var result: T? = null
        if (nesting.enter() && started.get()) {
            totalNs.addAndGet(measureNanoTime { result = block() })
        } else {
            result = block()
        }
        nesting.exit()

        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

private class NestingCounter : ThreadLocal<Int>() {
    override fun initialValue(): Int = 0

    fun enter(): Boolean {
        val v = get()
        set(v + 1)
        return v == 0
    }

    fun exit() {
        set(get() - 1)
    }
}

private object WATCHES {
    private val registered = ConcurrentHashMap.newKeySet<RsWatch>()

    init {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                println("\nWatches:")
                for (watch in registered.sortedBy { -it.totalNs.get() }) {
                    val ms = watch.totalNs.get() / 1_000_000
                    println("  ${ms.toString().padEnd(4)} ms ${watch.name}")
                }
            }
        })
    }

    operator fun plusAssign(watch: RsWatch) {
        registered += watch
    }
}
