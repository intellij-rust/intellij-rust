package org.rust.lang.utils

import com.intellij.openapi.Disposable

/**
 * Cookie-helper allowing to mutate the state of the supplied object (exception-safe).
 *
 * @t       Target which state is to be mutated
 * @set     Setter mutating the state of @t (ought to return previously set value of that particular property
 * @new     Value to be set
 */
internal class Cookie<T, V>(val t: T, val set: T.(V) -> V, new: V) : Disposable {
    val old = t.set(new)

    override fun dispose() {
        t.set(old);
    }
}


/**
 * Helper disposing `d` upon completing the execution of the `block`
 *
 * @d       Target `Disposable` to be disposed upon completion of the @block
 * @block   Target block to be run prior to disposal of @d
 */
public fun <T> using(d: Disposable, block: () -> T): T {
    try {
        return block()
    } finally {
        d.dispose()
    }
}

