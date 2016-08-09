package org.rust.utils

/**
 * Transforms seconds into milliseconds
 */
val Int.seconds: Int
    get() = this * 1000

/**
 * Converts [Boolean] to [Int]
 */
val Boolean.int: Int
    get() = if (this === true) 1 else 0

