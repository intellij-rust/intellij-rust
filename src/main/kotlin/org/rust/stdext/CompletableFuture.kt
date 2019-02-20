/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier

// :-(
// https://hackage.haskell.org/package/base-4.10.0.0/docs/Data-Traversable.html
fun <T> List<CompletableFuture<T>>.joinAll(): CompletableFuture<List<T>> =
    CompletableFuture.allOf(*this.toTypedArray()).thenApply { map { it.join() } }

fun <T> supplyAsync(executor: Executor, supplier: () -> T): CompletableFuture<T> =
    CompletableFuture.supplyAsync(Supplier { supplier() }, executor)
