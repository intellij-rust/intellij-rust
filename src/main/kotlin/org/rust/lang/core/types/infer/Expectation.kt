/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.types.ty.*

/**
 * When type-checking an expression, we propagate downward
 * whatever type hint we are able in the form of an `org.rust.lang.core.types.infer.Expectation`
 *
 * Follows https://github.com/rust-lang/rust/blob/master/compiler/rustc_typeck/src/check/expectation.rs#L11
 */
@Suppress("KDocUnresolvedReference")
sealed class Expectation {
    /** We know nothing about what type this expression should have */
    object NoExpectation : Expectation()

    /** This expression should have the type given (or some subtype) */
    class ExpectHasType(val ty: Ty) : Expectation()

    /** This expression will be cast to the `Ty` */
    class ExpectCastableToType(val ty: Ty) : Expectation()

    /**
     * This rvalue expression will be wrapped in `&` or `Box` and coerced
     * to `&Ty` or `Box<Ty>`, respectively. `Ty` is `[A]` or `Trait`
     */
    class ExpectRvalueLikeUnsized(val ty: Ty) : Expectation()

    private fun resolve(ctx: RsInferenceContext): Expectation {
        return when (this) {
            is ExpectHasType -> ExpectHasType(ctx.resolveTypeVarsIfPossible(ty))
            is ExpectCastableToType -> ExpectCastableToType(ctx.resolveTypeVarsIfPossible(ty))
            is ExpectRvalueLikeUnsized -> ExpectRvalueLikeUnsized(ctx.resolveTypeVarsIfPossible(ty))
            else -> this
        }
    }

    fun tyAsNullable(ctx: RsInferenceContext): Ty? {
        return when (val resolved = this.resolve(ctx)) {
            is ExpectHasType -> resolved.ty
            is ExpectCastableToType -> resolved.ty
            is ExpectRvalueLikeUnsized -> resolved.ty
            else -> null
        }
    }

    /**
     * It sometimes happens that we want to turn an expectation into
     * a **hard constraint** (i.e., something that must be satisfied
     * for the program to type-check). `only_has_type` will return
     * such a constraint, if it exists
     */
    fun onlyHasTy(ctx: RsInferenceContext): Ty? {
        return when (this) {
            is ExpectHasType -> ctx.resolveTypeVarsIfPossible(ty)
            else -> null
        }
    }

    companion object {
        /**
         * Provides an expectation for an rvalue expression given an *optional*
         * hint, which is not required for type safety (the resulting type might
         * be checked higher up, as is the case with `&expr` and `box expr`), but
         * is useful in determining the concrete type.
         *
         * The primary use case is where the expected type is a fat pointer,
         * like `&[isize]`. For example, consider the following statement:
         *
         *    let x: &[isize] = &[1, 2, 3];
         *
         * In this case, the expected type for the `&[1, 2, 3]` expression is
         * `&[isize]`. If however we were to say that `[1, 2, 3]` has the
         * expectation `ExpectHasType([isize])`, that would be too strong --
         * `[1, 2, 3]` does not have the type `[isize]` but rather `[isize; 3]`.
         * It is only the `&[1, 2, 3]` expression as a whole that can be coerced
         * to the type `&[isize]`. Therefore, we propagate this more limited hint,
         * which still is useful, because it informs integer literals and the like
         */
        fun rvalueHint(ty: Ty): Expectation {
            return when (ty.structTail() ?: ty) {
                is TyUnknown -> NoExpectation
                is TySlice, is TyTraitObject, is TyStr -> ExpectRvalueLikeUnsized(ty)
                else -> ExpectHasType(ty)
            }
        }

        fun maybeHasType(ty: Ty?): Expectation {
            return if (ty == null || ty is TyUnknown) {
                NoExpectation
            } else {
                ExpectHasType(ty)
            }
        }
    }
}
