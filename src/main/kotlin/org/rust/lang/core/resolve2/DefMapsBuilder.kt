/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.progress.ProgressIndicator
import org.rust.lang.core.crate.Crate
import org.rust.openapiext.computeInReadActionWithWriteActionPriority
import org.rust.stdext.getWithRethrow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
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

    /** We don't use [CountDownLatch] because [CompletableFuture] allows easier exception handling */
    private val remainingNumberCrates: AtomicInteger = AtomicInteger(crates.size)
    private val completableFuture: CompletableFuture<Unit> = CompletableFuture()

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
        completableFuture.getWithRethrow()
    }

    private fun buildSync() {
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
                completableFuture.completeExceptionally(e)
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
        if (completableFuture.isCompletedExceptionally) return

        crate.reverseDependencies.forEach { onDependencyCrateFinished(it) }
        if (remainingNumberCrates.decrementAndGet() == 0) {
            completableFuture.complete(Unit)
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
