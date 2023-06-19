/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import java.util.concurrent.locks.ReentrantLock

/**
 * Similar to [lazy], but uses cancellable locking
 */
fun <T> cancelableLazy(initializer: () -> T): Lazy<T> = SynchronizedCancellableLazyImpl(initializer)

/**
 * Mimics [SynchronizedLazyImpl], but uses cancellable locking
 */
private class SynchronizedCancellableLazyImpl<out T>(initializer: () -> T) : Lazy<T> {
    private var initializer: (() -> T)? = initializer
    @Volatile private var _value: Any? = UninitializedValue
    private val lock = ReentrantLock()

    @Suppress("UNCHECKED_CAST")
    override val value: T
        get() {
            val v1 = _value
            if (v1 !== UninitializedValue) {
                return v1 as T
            }

            return lock.withLockAndCheckingCancelled() {
                val v2 = _value
                if (v2 !== UninitializedValue) {
                    v2 as T
                } else {
                    val typedValue = initializer!!()
                    _value = typedValue
                    initializer = null
                    typedValue
                }
            }
        }

    override fun isInitialized(): Boolean = _value !== UninitializedValue

    override fun toString(): String {
        return if (isInitialized()) value.toString() else "Lazy value not initialized yet."
    }
}

private object UninitializedValue
