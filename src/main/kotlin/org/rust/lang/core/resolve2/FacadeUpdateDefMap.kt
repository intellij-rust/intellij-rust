/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.rust.RsTask.TaskType.*
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.crate.crateGraph
import org.rust.openapiext.*
import org.rust.stdext.getWithRethrow
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Lock
import kotlin.system.measureTimeMillis

/**
 * Returns defMap stored in [DefMapHolder] if it is up-to-date.
 * Otherwise rebuilds if needed [CrateDefMap] for [crate] and all its dependencies.
 * If process is cancelled ([ProcessCanceledException]), then only part of defMaps could be updated,
 * other defMaps will be updated in next call to [getOrUpdateIfNeeded].
 */
fun DefMapService.getOrUpdateIfNeeded(crate: CratePersistentId): CrateDefMap? {
    check(isNewResolveEnabled)
    val holder = getDefMapHolder(crate)

    if (holder.hasLatestStamp()) return holder.defMap

    checkReadAccessAllowed()
    checkIsSmartMode(project)
    return defMapsBuildLock.withLockAndCheckingCancelled {
        check(defMapsBuildLock.holdCount == 1)
        if (holder.hasLatestStamp()) return@withLockAndCheckingCancelled holder.defMap

        val pool = Executors.newWorkStealingPool()
        try {
            val indicator = ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator()
            // TODO: Invoke outside of read action ?
            DefMapUpdater(crate, this, pool, indicator, multithread = true).run()
            if (holder.defMap != null) holder.checkHasLatestStamp()
            holder.defMap
        } finally {
            pool.shutdown()
        }
    }
}

/** Called from macro expansion task */
fun updateDefMapForAllCrates(project: Project, pool: Executor, indicator: ProgressIndicator, multithread: Boolean = true) {
    if (!isNewResolveEnabled) return
    val dumbService = DumbService.getInstance(project)
    val defMapService = project.defMapService

    executeUnderProgressWithWriteActionPriorityWithRetries(indicator) {
        runReadActionInSmartMode(dumbService) {
            defMapService.defMapsBuildLock.withLockAndCheckingCancelled {
                check(defMapService.defMapsBuildLock.holdCount == 1)
                DefMapUpdater(rootCrateId = null, defMapService, pool, indicator, multithread).run()
            }
        }
    }
}

private class DefMapUpdater(
    /**
     * If null, DefMap is updated for all crates.
     * Otherwise for [rootCrateId] and all it dependencies.
     */
    rootCrateId: CratePersistentId?,
    private val defMapService: DefMapService,
    private val pool: Executor,
    private val indicator: ProgressIndicator,
    multithread: Boolean,
) {
    // Note: we can use only current thread if we are inside write action
    // (read action will not be started in other threads)
    private val multithread: Boolean = multithread && !ApplicationManager.getApplication().isWriteAccessAllowed
    private val topSortedCrates: List<Crate> = defMapService.project.crateGraph.topSortedCrates

    /** Crates to check for update */
    private val crates: Collection<Crate> = run {
        val rootCrate = rootCrateId?.let { id -> topSortedCrates.find { it.id == id } }
        if (rootCrate != null) rootCrate.flatDependencies + rootCrate else topSortedCrates
    }
    private var numberUpdatedCrates: Int = 0

    fun run() {
        checkReadAccessAllowed()
        val time = measureTimeMillis {
            executeUnderProgress(indicator) {
                doRun()
            }
        }
        if (numberUpdatedCrates > 0) {
            val cratesCount = if (numberUpdatedCrates == topSortedCrates.size) "all" else numberUpdatedCrates.toString()
            RESOLVE_LOG.info("Updated $cratesCount DefMaps in $time ms")
        }
    }

    private fun doRun() {
        check(isNewResolveEnabled)
        if (crates.isEmpty()) return
        indicator.checkCanceled()

        val cratesToCheck = findCratesToCheck()
        if (cratesToCheck.isEmpty()) return

        val cratesToUpdate = findCratesToUpdate(cratesToCheck)
        if (cratesToUpdate.isEmpty()) return

        defMapService.removeStaleDefMaps(topSortedCrates)

        val cratesToUpdateAll = getCratesToUpdateWithReversedDependencies(cratesToUpdate)
        val builtDefMaps = getBuiltDefMaps(cratesToUpdateAll)

        val cratesToUpdateAllSorted = cratesToUpdateAll.topSort(topSortedCrates)
        val pool = getPool(cratesToUpdateAllSorted.size)
        numberUpdatedCrates = cratesToUpdateAllSorted.size
        DefMapsBuilder(defMapService, cratesToUpdateAllSorted, builtDefMaps, indicator, pool).build()
    }

    private fun findCratesToCheck(): List<Pair<Crate, DefMapHolder>> {
        checkReadAccessAllowed()
        val cratesToCheck = mutableListOf<Pair<Crate, DefMapHolder>>()
        for (crate in crates) {
            val crateId = crate.id ?: continue
            val holder = defMapService.getDefMapHolder(crateId)
            if (!holder.hasLatestStamp()) {
                cratesToCheck += Pair(crate, holder)
            }
        }
        return cratesToCheck
    }

    private fun findCratesToUpdate(cratesToCheck: List<Pair<Crate, DefMapHolder>>): List<Crate> {
        val pool = getPool(cratesToCheck.size)
        val cratesToUpdate = if (pool is SameThreadExecutor) {
            cratesToCheck.filter { (crate, holder) ->
                holder.updateShouldRebuild(crate)
            }
        } else {
            cratesToCheck.filterAsync(pool) { (crate, holder) ->
                computeInReadActionWithWriteActionPriority(SensitiveProgressWrapper(indicator)) {
                    holder.updateShouldRebuild(crate)
                }
            }
        }
        return cratesToUpdate.map { it.first }
    }

    private fun getCratesToUpdateWithReversedDependencies(cratesToUpdate: List<Crate>): HashSet<Crate> {
        val cratesToUpdateWithReversedDependencies = cratesToUpdate.withReversedDependencies()
        for (crate in cratesToUpdateWithReversedDependencies) {
            val holder = defMapService.getDefMapHolder(crate.id ?: continue)
            // schedule rebuild for reverse dependencies of [cratesToUpdate],
            // so if current process is cancelled, they will be rebuilt next time
            holder.shouldRebuild = true
        }
        // Crates from [cratesToUpdateWithReversedDependencies] not in [crates] will be updated in future
        return crates.filterTo(hashSetOf()) { it in cratesToUpdateWithReversedDependencies }
    }

    private fun getBuiltDefMaps(cratesToUpdateAll: Set<Crate>): Map<Crate, CrateDefMap> {
        return crates
            .filter { it !in cratesToUpdateAll }
            .mapNotNull {
                val crateId = it.id ?: return@mapNotNull null
                val defMap = defMapService.getDefMapHolder(crateId).defMap ?: return@mapNotNull null
                it to defMap
            }
            .toMap(hashMapOf())
    }

    private fun getPool(size: Int) = if (multithread && size > 1) pool else SameThreadExecutor()
}

private fun List<Crate>.withReversedDependencies(): Set<Crate> {
    val result = hashSetOf<Crate>()
    fun processCrate(crate: Crate) {
        if (crate.id == null || !result.add(crate)) return
        for (reverseDependency in crate.reverseDependencies) {
            processCrate(reverseDependency)
        }
    }
    for (crate in this) {
        processCrate(crate)
    }
    return result
}

private fun Set<Crate>.topSort(topSortedCrates: List<Crate>): List<Crate> =
    topSortedCrates.filter { it in this }

class SameThreadExecutor : Executor {
    override fun execute(action: Runnable) = action.run()
}

/** Does not persist order of elements */
private fun <T> Collection<T>.filterAsync(pool: Executor, predicate: (T) -> Boolean): List<T> {
    val result = ConcurrentLinkedQueue<T>()
    val future = CompletableFuture<Unit>()
    val remainingCount = AtomicInteger(size)

    for (element in this) {
        pool.execute {
            if (future.isCompletedExceptionally) return@execute
            try {
                if (predicate(element)) {
                    result += element
                }
                if (remainingCount.decrementAndGet() == 0) {
                    future.complete(Unit)
                }
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
    }

    future.getWithRethrow()
    return result.toList()
}

private fun <T> Lock.withLockAndCheckingCancelled(timeoutMilliseconds: Int = 10, action: () -> T): T =
    ProgressIndicatorUtils.computeWithLockAndCheckingCanceled<T, Exception>(this, timeoutMilliseconds, TimeUnit.MILLISECONDS, action)
