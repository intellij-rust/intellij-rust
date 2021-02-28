/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

sealed class RsResult<T, E> {
    data class Ok<T, E>(val ok: T) : RsResult<T, E>()
    data class Err<T, E>(val err: E) : RsResult<T, E>()

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    fun ok(): T? = when (this) {
        is Ok -> ok
        is Err -> null
    }

    fun err(): E? = when (this) {
        is Ok -> null
        is Err -> err
    }

    inline fun <U> map(mapper: (T) -> U): RsResult<U, E> = when (this) {
        is Ok -> Ok(mapper(ok))
        is Err -> Err(err)
    }

    inline fun <U> andThen(action: (T) -> RsResult<U, E>): RsResult<U, E> = when (this) {
        is Ok -> action(ok)
        is Err -> Err(err)
    }

    inline fun unwrapOrElse(op: (E) -> T): T = when (this) {
        is Ok -> ok
        is Err -> op(err)
    }
}
