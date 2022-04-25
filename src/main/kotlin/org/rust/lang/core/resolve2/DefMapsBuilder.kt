/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.google.common.util.concurrent.SettableFuture
import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.progress.ProgressIndicator
import org.rust.lang.core.crate.Crate
import org.rust.openapiext.computeInReadActionWithWriteActionPriority
import org.rust.stdext.getWithRethrow
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/** Builds [CrateDefMap] for [crates] in parallel using [pool] and with respect to dependency graph */
class DefMapsBuilder(
    private val defMapService: DefMapService,
    private val crates: List<Crate>,  // should be top sorted
    defMaps: Map<Crate, CrateDefMap>,
    private val indicator: ProgressIndicator,
    private val pool: ExecutorService?,
    private val poolForMacros: ExecutorService?,
) {

    init {
        check(crates.isNotEmpty())
    }

    /** Values - number of dependencies for which [CrateDefMap] is not build yet */
    private val remainingDependenciesCounts: Map<Crate, AtomicInteger> = run {
        val cratesSet = crates.toSet()
        crates.associateWithTo(hashMapOf()) {
            val remainingDependencies = it.dependencies
                .filter { dep -> dep.crate in cratesSet }
                .size
            AtomicInteger(remainingDependencies)
        }
    }
    private val builtDefMaps: MutableMap<Crate, CrateDefMap> = ConcurrentHashMap(defMaps)

    /** We don't use [CountDownLatch] because [Future] allows easier exception handling */
    private val remainingNumberCrates: AtomicInteger = AtomicInteger(crates.size)
    private val future: SettableFuture<Unit> = SettableFuture.create()

    /** Only for profiling */
    private val tasksTimes: MutableMap<Crate, Long> = ConcurrentHashMap()

    fun build() {
        val wallTime = measureTimeMillis {
            if (pool != null) {
                buildAsync()
            } else {
                buildSync()
            }
        }

        printTimeStatistics(wallTime)
    }

    private fun buildAsync() {
        val cratesWithoutDependencies = remainingDependenciesCounts
            .filterValues { it.get() == 0 }
            .keys
        // task scheduling must be after all `it.get() == 0` checks
        for (crate in cratesWithoutDependencies) {
            buildDefMapAsync(crate)
        }
        future.getWithRethrow()
    }

    private fun buildSync() {
        if (poolForMacros == null) {
            doBuildSync()
        } else {
            invokeWithoutHelpingOtherForkJoinPools(poolForMacros) {
                computeInReadActionWithWriteActionPriority(SensitiveProgressWrapper(indicator)) {
                    doBuildSync()
                }
            }
        }
    }

    private fun doBuildSync() {
        for (crate in crates) {
            tasksTimes[crate] = measureTimeMillis {
                doBuildDefMap(crate)
            }
        }
    }

    private fun buildDefMapAsync(crate: Crate) {
        check(pool != null)
        pool.execute {
            try {
                check(crate !in tasksTimes)
                tasksTimes[crate] = measureTimeMillis {
                    computeInReadActionWithWriteActionPriority(SensitiveProgressWrapper(indicator)) {
                        doBuildDefMap(crate)
                    }
                }
            } catch (e: Throwable) {
                future.setException(e)
                return@execute
            }
            onCrateFinished(crate)
        }
    }

    private fun doBuildDefMap(crate: Crate) {
        val crateId = crate.id ?: return
        val allDependenciesDefMaps = crate.flatDependencies
            .mapNotNull {
                // it can be null e.g. if dependency has null id
                val dependencyDefMap = builtDefMaps[it] ?: return@mapNotNull null
                it to dependencyDefMap
            }
            .toMap(hashMapOf())
        val defMap = buildDefMap(crate, allDependenciesDefMaps, poolForMacros, indicator, isNormalCrate = true)
        defMapService.setDefMap(crateId, defMap)
        if (defMap != null) {
            builtDefMaps[crate] = defMap
        }
    }

    private fun onCrateFinished(crate: Crate) {
        /** Here we want to check for any exceptions, and `isDone` is equivalent check */
        if (future.isDone) return

        crate.reverseDependencies.forEach { onDependencyCrateFinished(it) }
        if (remainingNumberCrates.decrementAndGet() == 0) {
            future.set(Unit)
        }
    }

    private fun onDependencyCrateFinished(crate: Crate) {
        val count = remainingDependenciesCounts[crate] ?: return
        if (count.decrementAndGet() == 0) {
            buildDefMapAsync(crate)
        }
    }

    private fun printTimeStatistics(wallTime: Long) {
        if (!RESOLVE_LOG.isDebugEnabled) return
        check(tasksTimes.size == crates.size)
        val totalTime = tasksTimes.values.sum()
        val top5crates = tasksTimes.entries
            .sortedByDescending { (_, time) -> time }
            .take(5)
            .joinToString { (crate, time) -> "$crate ${time}ms" }
        val multithread = pool != null
        if (multithread) {
            RESOLVE_LOG.debug(
                "wallTime: $wallTime, totalTime: $totalTime, " +
                    "parallelism coefficient: ${"%.2f".format((totalTime.toDouble() / wallTime))}.    " +
                    "Top 5 crates: $top5crates"
            )
        } else {
            RESOLVE_LOG.debug("wallTime: $wallTime.    Top 5 crates: $top5crates")
        }
    }
}

/**
 * Needed because of [DefCollector.expandMacrosInParallel].
 * We use [ForkJoinPool.invokeAll] there, which can execute tasks
 * from completely different thread pool (associated with current thread).
 * That's why we need to be sure that we build DefMaps on thread associated with our [ForkJoinPool].
 * Also see [ResolveCommonThreadPool.pool].
 */
private fun invokeWithoutHelpingOtherForkJoinPools(forkJoinPool: ExecutorService, action: () -> Unit) {
    val future = SettableFuture.create<Unit>()
    forkJoinPool.submit {
        try {
            action()
        } catch (e: Throwable) {
            future.setException(e)
        }
        future.set(Unit)
    }
    future.getWithRethrow()
}
