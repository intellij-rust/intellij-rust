/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture

@kotlin.Suppress("unused")
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
                     task: AsyncTaskCtx<T>.() -> TaskResult<T>): CompletableFuture<TaskResult<T>> {
    val fut = CompletableFuture<TaskResult<T>>()
    queue.run(object : Task.Backgroundable(project, title) {
        override fun run(indicator: ProgressIndicator) {
            val ctx = object : AsyncTaskCtx<T> {
                override val progress: ProgressIndicator get() = indicator
            }
            fut.complete(ctx.task())
        }

        override fun onThrowable(error: Throwable) {
            fut.completeExceptionally(error)
        }
    })
    return fut
}
