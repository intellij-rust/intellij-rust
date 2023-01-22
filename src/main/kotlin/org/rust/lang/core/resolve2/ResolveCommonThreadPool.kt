/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.testFramework.common.ThreadLeakTracker
import org.rust.openapiext.isUnitTestMode
import java.util.concurrent.ExecutorService
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

    init {
        if (isUnitTestMode) {
            ThreadLeakTracker.longRunningThreadCreated(this, THREAD_NAME_PREFIX)
        }
    }

    private fun createPool(): ExecutorService {
        val parallelism = Runtime.getRuntime().availableProcessors()
        val threadFactory = ForkJoinWorkerThreadFactory { pool ->
            ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool).apply {
                name = "$THREAD_NAME_PREFIX$poolIndex"
            }
        }
        return ForkJoinPool(parallelism, threadFactory, null, true)
    }

    override fun dispose() {
        pool.shutdown()
    }

    companion object {

        private const val THREAD_NAME_PREFIX = "Rust-resolve-thread-"

        fun get(): ExecutorService = service<ResolveCommonThreadPool>().pool
    }
}
