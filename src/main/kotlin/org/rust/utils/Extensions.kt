package org.rust.utils

/**
 * Transforms seconds into milliseconds
 */
val Int.seconds: Int
    get() = this * 1000

/**
 * Performs unchecked cast upon the values of the iterable
 */
fun <T, E> Iterable<E>.cast(klass: Class<T>): Iterable<T> =
    map { it as T }
