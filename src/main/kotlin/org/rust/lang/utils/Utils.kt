package org.rust.lang.utils

import com.intellij.openapi.Disposable
import java.util.concurrent.locks.Lock


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

/**
 * Helper executing the block supplied under the lock being ACQUIRED,
 * and RELEASED upon completion (successful or not)
 *
 * @l       Lock to be acquired
 * @block   Target block to be run 'under' the lock being acquired
 */
fun <T> lock(l: Lock, block: () -> T): T =
    l.lock().let {
        using(Disposable { l.unlock() }, block)
    }


/**
 * Helper executing the block supplied under the lock being RELEASED,
 * and ACQUIRED upon completion (successful or not), i.e. it performs in the
 * way exactly opposite to [lock]
 *
 * @l       Lock to be release
 * @block   Target block to be run 'under' the lock being released
 */
fun <T> release(l: Lock, block: () -> T): T =
    l.unlock().let {
        using(Disposable { l.lock() }, block)
    }
