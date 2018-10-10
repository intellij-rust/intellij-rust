/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

typealias ShouldStopIteration = Boolean
typealias Consumer<T> = (T) -> ShouldStopIteration
private typealias ForEachFun<T> = (Consumer<T>) -> Boolean

interface InternalIterator<out T> {
    fun forEach(consumer: Consumer<T>): Boolean

    companion object {
        fun <T> fromFun(forEachFun: ForEachFun<T>): InternalIterator<T> = object : InternalIterator<T> {
            override fun forEach(consumer: Consumer<T>): Boolean {
                return forEachFun(consumer)
            }
        }
    }
}

fun <T> ((Consumer<T>) -> Boolean).toInternalIterator(): InternalIterator<T> =
    InternalIterator.fromFun { this(it) }

fun <P1, T> ((P1, Consumer<T>) -> Boolean).toInternalIterator(p1: P1): InternalIterator<T> =
    InternalIterator.fromFun { this(p1, it) }

fun <P1, P2, T> ((P1, P2, Consumer<T>) -> Boolean).toInternalIterator(p1: P1, p2: P2): InternalIterator<T> =
    InternalIterator.fromFun { this(p1, p2, it) }

fun <P1, P2, P3, T> ((P1, P2, P3, Consumer<T>) -> Boolean).toInternalIterator(p1: P1, p2: P2, p3: P3): InternalIterator<T> =
    InternalIterator.fromFun { this(p1, p2, p3, it) }

fun <T> InternalIterator<T>.filter(predicate: (T) -> Boolean): InternalIterator<T> =
    FilterInternalIterator(this, predicate)

fun <T> InternalIterator<T>.takeWhile(predicate: (T) -> Boolean): InternalIterator<T> =
    TakeWhileInternalIterator(this, predicate)

fun <T, R> InternalIterator<T>.map(transform: (T) -> R): InternalIterator<R> =
    MapInternalIterator(this, transform)

fun <T, R: Any> InternalIterator<T>.mapNotNull(transform: (T) -> R?): InternalIterator<R> =
    MapNotNullInternalIterator(this, transform)

fun <T, U> InternalIterator<T>.forEachUnstopable(consumer: (T) -> U) {
    forEach {
        consumer(it)
        false
    }
}

fun <T> InternalIterator<T>.toList(): List<T> {
    val list = mutableListOf<T>()
    forEachUnstopable(list::add)
    return list
}

inline fun <reified T> InternalIterator<T>.toTypedArray(): Array<T> =
    toList().toTypedArray()

fun <T> InternalIterator<T>.partition(predicate: (T) -> Boolean): Pair<List<T>, List<T>> {
    val list1 = mutableListOf<T>()
    val list2 = mutableListOf<T>()
    forEachUnstopable {
        if (predicate(it)) {
            list1.add(it)
        } else {
            list2.add(it)
        }
    }
    return list1 to list2
}

private class FilterInternalIterator<T>(
    private val nested: InternalIterator<T>,
    private val predicate: (T) -> Boolean
) : InternalIterator<T> {
    override fun forEach(consumer: Consumer<T>): Boolean {
        return nested.forEach {
            if (predicate(it)) {
                consumer(it)
            } else {
                false
            }
        }
    }
}

private class TakeWhileInternalIterator<T>(
    private val nested: InternalIterator<T>,
    private val predicate: (T) -> Boolean
) : InternalIterator<T> {
    override fun forEach(consumer: Consumer<T>): Boolean {
        return nested.forEach {
            if (predicate(it)) {
                consumer(it)
            } else {
                true // stop iterating
            }
        }
    }
}

private class MapInternalIterator<T, R>(
    private val nested: InternalIterator<T>,
    private val transform: (T) -> R
) : InternalIterator<R> {
    override fun forEach(consumer: Consumer<R>): Boolean {
        return nested.forEach {
            consumer(transform(it))
        }
    }
}

private class MapNotNullInternalIterator<T, R: Any>(
    private val nested: InternalIterator<T>,
    private val transform: (T) -> R?
) : InternalIterator<R> {
    override fun forEach(consumer: Consumer<R>): Boolean {
        return nested.forEach {
            val result = transform(it)
            if (result != null) consumer(result) else false
        }
    }
}
