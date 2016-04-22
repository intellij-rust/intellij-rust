package org.rust.cargo.util

import com.google.common.util.concurrent.*
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.util.concurrency.AppExecutorUtil

object TasksUtil

/**
 * Schedules task to run asynchronously (on the application thread-pool)
 *
 * NOTA BENE: This would occupy 2 (sic!) threads: one carrying payload, and the second one to piggyback results of the
 *            former right into `ListenableFuture`. We need to address that
 */
fun Task.Backgroundable.enqueue(): ListenableFuture<*> =
    JdkFutureAdapters.listenInPoolThread(
        (ProgressManager.getInstance() as CoreProgressManager).runProcessWithProgressAsynchronously(this),
        AppExecutorUtil.getAppExecutorService()
        )


/**
 * Allows to bind one future to the other one, 'piggybacking' results
 * of the former into the latter one
 */
inline fun <reified T> ListenableFuture<T>.flowInto(f: SettableFuture<T>) {
    check(!f.isDone && !f.isCancelled, { "Target future should not be done or cancelled at that point!" })

    Futures.addCallback(this, object: FutureCallback<T> {
        override fun onSuccess(result: T?) {
            f.set(result)
        }
        override fun onFailure(t: Throwable?) {
            f.setException(t)
        }
    })
}

/**
 * Same as [flowInto], except that it neatly handles the cases of the 'erased' interfaces,
 * ignoring the result of the source future, and effectively just binding those two
 */
fun <T> ListenableFuture<*>.flowIntoDiscardingResult(f: SettableFuture<T>, value: () -> T): ListenableFuture<T> {
    check(!f.isDone && !f.isCancelled, { "Target future should not be done or cancelled at that point!" })

    Futures.addCallback(this, object: FutureCallback<Any> {
        override fun onSuccess(result: Any?) {
            f.set(value())
        }
        override fun onFailure(t: Throwable?) {
            f.setException(t)
        }
    })

    return f
}

