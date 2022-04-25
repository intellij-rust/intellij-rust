/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.testFramework.ThreadTracker
import org.rust.openapiext.isUnitTestMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent.ForkJoinTask

@Service
class ResolveCommonThreadPool : Disposable {

    /**
     * We must use a separate pool because:
     * - [ForkJoinPool.commonPool] is heavily used by the platform
     * - [ForkJoinPool] can start execute a task when joining ([ForkJoinTask.get]) another task
     */
    private val pool: ExecutorService = createPool()

    private fun createPool(): ExecutorService {
        return if (isUnitTestMode) {
            val parallelism = Runtime.getRuntime().availableProcessors()
            val threadFactory = ForkJoinWorkerThreadFactory { pool ->
                val thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool)
                ThreadTracker.longRunningThreadCreated(this, thread.name)
                thread
            }
            ForkJoinPool(parallelism, threadFactory, null, true)
        } else {
            Executors.newWorkStealingPool()
        }
    }

    override fun dispose() {
        pool.shutdown()
    }

    companion object {
        fun get(): ExecutorService = service<ResolveCommonThreadPool>().pool
    }
}
