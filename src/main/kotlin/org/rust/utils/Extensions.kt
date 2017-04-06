package org.rust.utils

/**
 * Transforms seconds into milliseconds
 */
val Int.seconds: Int
    get() = this * 1000

