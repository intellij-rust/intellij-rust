package org.rust.utils

/**
 * Transforms seconds into milliseconds
 */
val Int.seconds: Int
    get() = this * 1000

/**
 * Downcasts the values of the iterable
 */
inline fun <reified T> Iterable<*>.cast(): Iterable<T> =
    map { it as T }
