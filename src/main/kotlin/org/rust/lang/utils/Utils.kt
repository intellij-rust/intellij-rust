package org.rust.lang.utils

import com.intellij.openapi.Disposable


/**
 * Helper disposing [d] upon completing the execution of the [block]
 *
 * @d       Target `Disposable` to be disposed upon completion of the @block
 * @block   Target block to be run prior to disposal of @d
 */
fun <T> using(d: Disposable, block: () -> T): T {
    try {
        return block()
    } finally {
        d.dispose()
    }
}

/**
 * Helper disposing [d] upon completing the execution of the [block] (under the [d])
 *
 * @d       Target `Disposable` to be disposed upon completion of the @block
 * @block   Target block to be run prior to disposal of @d
 */
fun <D: Disposable, T> usingWith(d: D, block: (D) -> T): T {
    try {
        return block(d)
    } finally {
        d.dispose()
    }
}
