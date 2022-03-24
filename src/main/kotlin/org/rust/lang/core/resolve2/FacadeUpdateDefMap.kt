/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.google.common.util.concurrent.SettableFuture
import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.util.ConcurrencyUtil.newSameThreadExecutorService
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.macros.MacroExpansionSharedCache
import org.rust.openapiext.*
import org.rust.stdext.getWithRethrow
import org.rust.stdext.withLockAndCheckingCancelled
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.withLock
import kotlin.system.measureTimeMillis

/**
 * Returns defMap stored in [DefMapHolder] if it is up-to-date.
 * Otherwise rebuilds if needed [CrateDefMap] for [crate] and all its dependencies.
 * If process is cancelled ([ProcessCanceledException]), then only part of defMaps could be updated,
 * other defMaps will be updated in next call to [getOrUpdateIfNeeded].
 */
fun DefMapService.getOrUpdateIfNeeded(crate: CratePersistentId): CrateDefMap? =
    getOrUpdateIfNeeded(listOf(crate))[crate]

fun DefMapService.getOrUpdateIfNeeded(crates: List<CratePersistentId>): Map<CratePersistentId, CrateDefMap?> {
    check(project.isNewResolveEnabled)
    val holders = crates.map(::getDefMapHolder)

    fun List<DefMapHolder>.defMaps() = associate { it.crateId to it.defMap }
    if (holders.all { it.hasLatestStamp() }) return holders.defMaps()

    checkReadAccessAllowed()
    checkIsSmartMode(project)
    return defMapsBuildLock.withLockAndCheckingCancelled {
        check(defMapsBuildLock.holdCount == 1) { "Can't use resolve while building CrateDefMap" }
        if (holders.all { it.hasLatestStamp() }) return@withLockAndCheckingCancelled holders.defMaps()

        val pool = Executors.newWorkStealingPool()
        try {
            val indicator = ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator()
            // TODO: Invoke outside of read action ?
            DefMapUpdater(crates, this, pool, indicator, multithread = true).run()
            for (holder in holders) {
                if (hasDefMapFor(holder.crateId)) {
                    holder.checkHasLatestStamp()
                }
            }
            holders.defMaps()
        } finally {
            pool.shutdown()
            MacroExpansionSharedCache.getInstance().flush()
        }
    }
}

/** Called from macro expansion task */
fun updateDefMapForAllCrates(
    project: Project,
    pool: ExecutorService,
    indicator: ProgressIndicator,
    multithread: Boolean = true
): List<CrateDefMap> {
    if (!project.isNewResolveEnabled) return emptyList()
    return executeUnderProgressWithWriteActionPriorityWithRetries(indicator) { wrappedIndicator ->
        doUpdateDefMapForAllCrates(project, pool, wrappedIndicator, multithread)
    }
}

private fun doUpdateDefMapForAllCrates(
    project: Project,
    pool: ExecutorService,
    indicator: ProgressIndicator,
    multithread: Boolean,
    rootCrateIds: List<CratePersistentId>? = null
): List<CrateDefMap> {
    val dumbService = DumbService.getInstance(project)
    val defMapService = project.defMapService
    return runReadActionInSmartMode(dumbService) {
        defMapService.defMapsBuildLock.withLockAndCheckingCancelled {
            check(defMapService.defMapsBuildLock.holdCount == 1)
            DefMapUpdater(rootCrateIds, defMapService, pool, indicator, multithread).run()
        }
    }
}

fun Project.forceRebuildDefMapForAllCrates(multithread: Boolean) {
    val pool = Executors.newWorkStealingPool()
    try {
        runReadAction {
            defMapService.defMapsBuildLock.withLock {
                defMapService.scheduleRebuildAllDefMaps()
            }
        }
        doUpdateDefMapForAllCrates(this, pool, EmptyProgressIndicator(), multithread)
    } finally {
        pool.shutdown()
    }
}

fun Project.forceRebuildDefMapForCrate(crateId: CratePersistentId) {
    runReadAction {
        defMapService.defMapsBuildLock.withLock {
            defMapService.scheduleRebuildDefMap(crateId)
        }
    }
    doUpdateDefMapForAllCrates(this, newSameThreadExecutorService(), EmptyProgressIndicator(), multithread = false, listOf(crateId))
}

fun Project.getAllDefMaps(): List<CrateDefMap> = crateGraph.topSortedCrates.mapNotNull {
    val id = it.id ?: return@mapNotNull null
    defMapService.getOrUpdateIfNeeded(id)
}

private class DefMapUpdater(
    /**
     * If null, DefMap is updated for all crates.
     * Otherwise for [rootCrateIds] and all its dependencies.
     */
    rootCrateIds: List<CratePersistentId>?,
    private val defMapService: DefMapService,
    private val pool: ExecutorService,
    private val indicator: ProgressIndicator,
    multithread: Boolean,
) {
    // Note: we can use only current thread if we are inside write action
    // (read action will not be started in other threads)
    private val multithread: Boolean = multithread && !ApplicationManager.getApplication().isWriteAccessAllowed
    private val topSortedCrates: List<Crate> = defMapService.project.crateGraph.topSortedCrates

    /** Crates to check for update */
    private val crates: Collection<Crate> = if (rootCrateIds == null) {
        topSortedCrates
    } else {
        val rootCrates = rootCrateIds.mapNotNull { id -> topSortedCrates.find { it.id == id } }
        val crates = rootCrates.flatMapTo(hashSetOf()) { it.flatDependencies } + rootCrates
        crates.topSort(topSortedCrates)
    }
    private var numberUpdatedCrates: Int = 0

    fun run(): List<CrateDefMap> {
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
        return crates.mapNotNull {
            val crateId = it.id ?: return@mapNotNull null
            defMapService.getDefMapHolder(crateId).defMap
        }
    }

    private fun doRun() {
        check(defMapService.project.isNewResolveEnabled)
        indicator.checkCanceled()

        val cratesToCheck = findCratesToCheck()
        val cratesToUpdate = findCratesToUpdate(cratesToCheck)

        defMapService.removeStaleDefMaps(topSortedCrates)
        if (cratesToUpdate.isEmpty()) return

        val cratesToUpdateAll = getCratesToUpdateWithReversedDependencies(cratesToUpdate)
        val builtDefMaps = getBuiltDefMaps(cratesToUpdateAll)

        val cratesToUpdateAllSorted = cratesToUpdateAll.topSort(topSortedCrates)
        val pool = pool.takeIf { multithread && cratesToUpdateAllSorted.size > 1 }
        val poolForMacros = this.pool.takeIf { multithread }
        numberUpdatedCrates = cratesToUpdateAllSorted.size
        DefMapsBuilder(defMapService, cratesToUpdateAllSorted, builtDefMaps, indicator, pool, poolForMacros).build()
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
        val cratesToUpdate = if (multithread && cratesToCheck.size > 1) {
            cratesToCheck.filterAsync(pool) { (crate, holder) ->
                computeInReadActionWithWriteActionPriority(SensitiveProgressWrapper(indicator)) {
                    holder.updateShouldRebuild(crate)
                }
            }
        } else {
            cratesToCheck.filter { (crate, holder) ->
                holder.updateShouldRebuild(crate)
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

/** Does not persist order of elements */
private fun <T> Collection<T>.filterAsync(pool: Executor, predicate: (T) -> Boolean): List<T> {
    val result = ConcurrentLinkedQueue<T>()
    val future = SettableFuture.create<Unit>()
    val remainingCount = AtomicInteger(size)

    for (element in this) {
        pool.execute {
            if (future.isDone) return@execute
            try {
                if (predicate(element)) {
                    result += element
                }
                if (remainingCount.decrementAndGet() == 0) {
                    future.set(Unit)
                }
            } catch (e: Throwable) {
                future.setException(e)
            }
        }
    }

    future.getWithRethrow()
    return result.toList()
}
