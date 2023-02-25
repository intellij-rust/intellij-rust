/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

sealed class RsResult<out T, out E> {
    data class Ok<T>(val ok: T) : RsResult<T, Nothing>()
    data class Err<E>(val err: E) : RsResult<Nothing, E>()

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

    inline fun <U> mapErr(mapper: (E) -> U): RsResult<T, U> = when (this) {
        is Ok -> Ok(ok)
        is Err -> Err(mapper(err))
    }

    fun unwrap(): T = when (this) {
        is Ok -> ok
        is Err -> if (err is Throwable) {
            throw IllegalStateException("called `RsResult.unwrap()` on an `Err` value", err)
        } else {
            throw IllegalStateException("called `RsResult.unwrap()` on an `Err` value: $err")
        }
    }
}

inline fun <T, E, U> RsResult<T, E>.andThen(action: (T) -> RsResult<U, E>): RsResult<U, E> = when (this) {
    is RsResult.Ok -> action(ok)
    is RsResult.Err -> RsResult.Err(err)
}

@Suppress("unused")
inline fun <T, E, F> RsResult<T, E>.orElse(op: (E) -> RsResult<T, F>): RsResult<T, F> = when (this) {
    is RsResult.Ok -> RsResult.Ok(ok)
    is RsResult.Err -> op(err)
}

inline fun <T, E> RsResult<T, E>.unwrapOrElse(op: (E) -> T): T = when (this) {
    is RsResult.Ok -> ok
    is RsResult.Err -> op(err)
}

fun <T, E: Throwable> RsResult<T, E>.unwrapOrThrow(): T = when (this) {
    is RsResult.Ok -> ok
    is RsResult.Err -> throw err
}

fun <T : Any> T?.toResult(): RsResult<T, Unit> = if (this != null) RsResult.Ok(this) else RsResult.Err(Unit)
