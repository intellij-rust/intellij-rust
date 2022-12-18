/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

/**
 * Represents general loop control flow, allowing either to yield a value from the current
 * iteration (`ControlFlow.Continue(value)`) or break out of the loop with a given return
 * value (`ControlFlow.Break(value)`).
 *
 * This class is intended to be a structured reification of loop control flow. A simpler
 * alternative would be to return a `Boolean`, with one value for continued iteration and
 * the other for early loop termination. `ControlFlow` makes it explicit which return value
 * is which, so one doesn't need to guess whether `true` means "go on" or "stop". It also
 * allows to return a value, which means that we can implement support for breaking with a
 * value out of a `for` loop (via the `tryForEach` combinator).
 *
 * This class is mirrored from
 * [`std::ops::ControlFlow`](https://doc.rust-lang.org/std/ops/enum.ControlFlow.html) enum in
 * Rust. It is less ergonomic and useful in Kotlin due to different design decisions (like
 * lack of `?` operator), but it is still useful for describing the control flow of recursive
 * method calls.
 *
 * # Example
 * ```kotlin
 * for (foo in bar) {
 *     if (foo is A) continue
 *     if (foo is B) break
 *     foo.frobnicate()
 * }
 * // We can equivalently rewrite the loop above as follows:
 * bar.tryForEach {
 *     if (foo is A) return@tryForEach CONTINUE
 *     if (foo is B) return@tryForEach BREAK
 *     foo.frobnicate()
 *     CONTINUE
 * }
 * ```
 */
sealed class ControlFlow<out B, out C> {
    /**
     * This variant should be returned from a function if the calling method is expected
     * to continue its iterations.
     *
     * Of course, there is nothing preventing the method from handling `Continue` variant
     * differently, e.g. continuing the iteration on `Break` variants and stopping on the
     * first `Continue`. For example, this is the way `ControlFlow.tryFirst` works. It tries
     * operations in a sequence until one of them is successful (returning `Continue`).
     *
     * The returned variant describes whether the returning method thinks that the iteration
     * should be continued.
     */
    data class Continue<C>(val value: C) : ControlFlow<Nothing, C>()

    /**
     * This variant should be returned from a function if the calling method is expected
     * to terminate its iterations, passing up the given `value`.
     *
     * Of course, there is nothing preventing the method from handling `Break` variant
     * differently, e.g. continuing the iteration on `Break` variants and stopping on the
     * first `Continue`. For example, this is the way `ControlFlow.tryFirst` works. It tries
     * operations in a sequence until one of them is successful (returning `Continue`).
     *
     * The returned variant describes whether the returning method thinks that the iteration
     * should be continued.
     */
    data class Break<B>(val value: B) : ControlFlow<B, Nothing>()

    val isBreak: Boolean
        get() = this is Break
    val isContinue: Boolean
        get() = this is Continue
}

/**
 * Transforms the given `ControlFlow` instance by mapping the value for the `Continue` variant
 * and leaving the `Break` variant as is.
 *
 * Note that you can discard a variant by using the mapping function which returns `Nothing`. For
 * example, this way you can implement early return for `Break` variants:
 * ```kotlin
 * val cf: ControlFlow<B, C> = doStuff();
 * // Unwrap the `Continue` variant, or early return on `Break` variant.
 * val cont = cf.mapBreak { b -> return adapt(b) }
 * ```
 */
inline fun <B1, B2, C> ControlFlow<B1, C>.mapBreak(f: (B1) -> B2): ControlFlow<B2, C> =
    when (this) {
        is ControlFlow.Break -> ControlFlow.Break(f(value))
        is ControlFlow.Continue -> this
    }

/**
 * Transforms the given `ControlFlow` instance by mapping the value for the `Break` variant
 * and leaving the `Continue` variant as is.
 *
 * Note that you can discard a variant by using the mapping function which returns `Nothing`. For
 * example, this way you can implement early return for `Continue` variants:
 * ```kotlin
 * val cf: ControlFlow<B, C> = doStuff();
 * // Unwrap the `Break` variant, or early return on `Continue` variant.
 * val brk = cf.mapContinue { c -> return adapt(c) }
 * ```
 */
inline fun <B, C1, C2> ControlFlow<B, C1>.mapContinue(f: (C1) -> C2): ControlFlow<B, C2> =
    when (this) {
        is ControlFlow.Continue -> ControlFlow.Continue(f(value))
        is ControlFlow.Break -> this
    }

/**
 * Combines two `ControlFlow` instances, returning the `Continue` value of the first `Continue`
 * instance, or returning the `Break` value of the last operand.
 */
inline fun <B1, B2, C> ControlFlow<B1, C>.or(f: (B1) -> ControlFlow<B2, C>): ControlFlow<B2, C> =
    when (this) {
        is ControlFlow.Continue -> this
        is ControlFlow.Break -> f(this.value)
    }

/**
 * Combines two `ControlFlow` instances, returning the `Break` value of the first `Break`
 * instance, or returning the `Continue` value of the last operand.
 */
inline fun <B, C1, C2> ControlFlow<B, C1>.and(f: (C1) -> ControlFlow<B, C2>): ControlFlow<B, C2> =
    when (this) {
        is ControlFlow.Continue -> f(this.value)
        is ControlFlow.Break -> this
    }

val CONTINUE = ControlFlow.Continue(Unit)
val BREAK = ControlFlow.Break(Unit)

/**
 * A type alias for the common case of control flow without any data payloads in
 * the variants. Its two instances are the constants `CONTINUE` and `BREAK`.
 */
typealias ShouldStop = ControlFlow<Unit, Unit>

/**
 * Performs the given operation on every element in the sequence. If any operation fails
 * (i.e. returns `ControlFlow.Break`), stops the iteration and returns that `Break` value.
 * If the loop finishes successfully, returns `CONTINUE`.
 */
inline fun <T, B> Sequence<T>.tryForEach(f: (T) -> ControlFlow<B, Unit>): ControlFlow<B, Unit> {
    for (elt in this) {
        val mapped = f(elt)
        if (mapped.isBreak) {
            return mapped
        }
    }
    return CONTINUE
}

/**
 * Performs the given operation on every element in the sequence. If any operation succeeds
 * (i.e. returns `ControlFlow.Continue`), stops the iteration and returns that `Continue` value.
 * If all operations fail, returns `BREAK`.
 */
inline fun <T, C> Sequence<T>.tryFirst(f: (T) -> ControlFlow<Unit, C>): ControlFlow<Unit, C> {
    for (elt in this) {
        f(elt).mapContinue { return ControlFlow.Continue(it) }
    }
    return BREAK
}

inline fun <T, B> Iterable<T>.tryForEach(f: (T) -> ControlFlow<B, Unit>): ControlFlow<B, Unit> =
    asSequence().tryForEach(f)

inline fun <T, C> Iterable<T>.tryFirst(f: (T) -> ControlFlow<Unit, C>): ControlFlow<Unit, C> =
    asSequence().tryFirst(f)
