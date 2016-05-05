package org.rust.cargo.util

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture

object TasksUtil

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

