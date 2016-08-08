package org.rust.utils

/**
 * XXX
 */
class Either<out Left, out Right> {

    val left: Left?
    val right: Right?

    private constructor (left: Left? = null, right: Right? = null) {
        this.left = left
        this.right = right
    }

    companion object {
        fun <R, L> left(left: L): Either<L, R> = Either(left = left)
        fun <R, L> right(right: R): Either<L, R> = Either(right = right)

        fun <Left, Right, T, R> apply(either: Either<Left, Right>, func: Function1<T, R>): R
            where Left : T, Right : T =
            either.left?.let { func(it) } ?: either.right?.let { func(it) }!!

    }
}
