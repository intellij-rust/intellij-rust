/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import com.intellij.openapi.diagnostic.Logger
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue


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

    private val updates: Queue<(T) -> CompletableFuture<Unit>> = ConcurrentLinkedQueue()
    private var running: Boolean = false

    val currentState: T get() = current

    fun updateAsync(updater: (T) -> CompletableFuture<T>): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        updates.add { current ->
            updater(current)
                .handle { next, err ->
                    if (err == null) {
                        this.current = next
                        result.complete(next)
                    } else {
                        LOG.error(err)
                        result.completeExceptionally(err)
                    }
                    Unit
                }
        }
        startUpdateProcessing()
        return result
    }

    fun updateSync(updater: (T) -> T): CompletableFuture<T> =
        updateAsync { CompletableFuture.completedFuture(updater(it)) }

    @Synchronized
    private fun startUpdateProcessing() {
        if (running || updates.isEmpty()) return
        val nextUpdate = updates.remove()
        running = true
        nextUpdate(current)
            .whenComplete { _, _ ->
                stopUpdateProcessing()
                startUpdateProcessing()
            }
    }

    @Synchronized
    private fun stopUpdateProcessing() {
        check(running)
        running = false
    }

    companion object {
        private val LOG = Logger.getInstance(AsyncValue::class.java)
    }
}

// :-(
// https://hackage.haskell.org/package/base-4.10.0.0/docs/Data-Traversable.html
fun <T> List<CompletableFuture<T>>.joinAll(): CompletableFuture<List<T>> =
    CompletableFuture.allOf(*this.toTypedArray()).thenApply { map { it.join() } }
