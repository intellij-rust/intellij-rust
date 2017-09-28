/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.utils

import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

sealed class TaskResult<out T> {
    class Ok<out T>(val value: T) : TaskResult<T>()
    class Err<out T>(val reason: String) : TaskResult<T>()
}

interface AsyncTaskCtx<T> {
    val progress: ProgressIndicator
    fun err(presentableMessage: String) = TaskResult.Err<T>(presentableMessage)
    fun ok(value: T) = TaskResult.Ok(value)
}

fun <T> runAsyncTask(project: Project, queue: BackgroundTaskQueue, title: String,
                     task: AsyncTaskCtx<T>.() -> TaskResult<T>): Promise<TaskResult<T>> {
    val fut = AsyncPromise<TaskResult<T>>()
    queue.run(object : Task.Backgroundable(project, title) {
        override fun run(indicator: ProgressIndicator) {
            val ctx = object : AsyncTaskCtx<T> {
                override val progress: ProgressIndicator get() = indicator
            }
            fut.setResult(ctx.task())
        }

        override fun onThrowable(error: Throwable) {
            fut.setError(error)
        }
    })
    return fut
}

/**
 * A container for an immutable value, which allows
 * reading and updating value safely concurrently.
 * [AsyncValue] is similar to Clojure's atom.
 *
 * [updateAsync] method is used to schedule a modification
 * of the form `(T) -> Promise<T>`. It is guaranteed that
 * all updates are serialized.
 */
class AsyncValue<T>(initial: T) {
    @Volatile
    private var current: T = initial

    private val updates: Queue<(T) -> Promise<Unit>> = ConcurrentLinkedQueue()
    private var running: Boolean = false

    val currentState: T get() = current

    fun updateAsync(updater: (T) -> Promise<T>): Promise<T> {
        val result = AsyncPromise<T>()
        updates.add { current ->
            updater(current)
                .done { next -> this.current = next }
                .apply { notify(result) }
                .then { Unit }
        }
        startUpdateProcessing()
        return result
    }

    fun updateSync(updater: (T) -> T): Promise<T> =
        updateAsync { Promise.resolve(updater(it)) }

    @Synchronized
    private fun startUpdateProcessing() {
        if (running || updates.isEmpty()) return
        val nextUpdate = updates.remove()
        running = true
        nextUpdate(current)
            .processed {
                stopUpdateProcessing()
                startUpdateProcessing()
            }
    }

    @Synchronized
    private fun stopUpdateProcessing() {
        check(running)
        running = false
    }
}
