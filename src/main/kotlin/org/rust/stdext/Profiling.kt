/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("unused")

package org.rust.stdext

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
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

    private fun <T> measureInternal(name: String, f: () -> T): T {
        var result: T? = null
        val time = measureTimeMillis { result = f() }
        valuesTotal.merge(name, time, Long::plus)
        invokes.merge(name, 1, Long::plus)
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    fun values(): Map<String, Long> {
        val result = LinkedHashMap<String, Long>()
        for ((k, sum) in valuesTotal) {
            result[k] = (sum.toDouble() / invokes[k]!!).toLong()
        }
        return result
    }
}

class ListTimings {
    private val timings: MutableMap<String, MutableList<Long>> = LinkedHashMap()

    fun add(t: Timings) {
        for ((k, v) in t.values()) {
            timings.computeIfAbsent(k) { mutableListOf() }.add(v)
        }
    }

    fun add(action: (Timings) -> Unit) {
        val t = Timings()
        action(t)
        add(t)
    }

    fun print() {
        val values = timings.mapValues { (_, v) -> calculate(v) }
        val table = listOf(listOf("LABEL", "MIN (ms)", "MAX (ms)", "AVG (ms)", "ERROR")) +
            values.map { (k, v) ->
                listOf(
                    k,
                    v.min.toString(),
                    v.max.toString(),
                    v.avg.toInt().toString(),
                    String.format("± %.2f", v.standardDeviation)
                )
            }
        val widths = IntArray(table[0].size)
        for (row in table) {
            row.forEachIndexed { i, s -> widths[i] = max(widths[i], s.length) }
        }
        for (row in table) {
            println(row.withIndex().joinToString(" ") { (i, s) ->
                s.padEnd(widths[i])
            })
        }
    }

    private fun calculate(values: MutableList<Long>): Statistics {
        val min = values.min() ?: error("Empty timings!")
        val max = values.max() ?: error("Empty timings!")
        val avg = values.sum() / values.size.toDouble()
        val variance = if (values.size > 1) {
            values.fold(0.0) { acc, i -> acc + (i - avg).pow(2.0) } / (values.size - 1)
        } else {
            Double.NaN
        }
        val standardDeviation = sqrt(variance)

        return Statistics(min, max, avg, standardDeviation)
    }
}

data class Statistics(
    val min: Long,
    val max: Long,
    val avg: Double,
    val standardDeviation: Double
)

fun repeatBenchmark(warmupIterations: Int = 10, iterations: Int = 10, action: (Timings) -> Unit) {
    repeatBenchmarkInternal(warmupIterations, "Warmup iteration", action)
    repeatBenchmarkInternal(iterations, "Iteration", action)
}

private fun repeatBenchmarkInternal(times: Int = 10, label: String, action: (Timings) -> Unit) {
    val timings = ListTimings()
    repeat(times) { i ->
        println("$label #${i + 1}")
        timings.add {
            action(it)
        }
        timings.print()
        println()
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
