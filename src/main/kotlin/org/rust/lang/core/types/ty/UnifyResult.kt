/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

/**
 * ```rust
 * struct S<T>(T);
 * #[derive(Eq)]
 * struct X;
 * struct Y;
 *
 * fn foo<T: Eq>(t: S<T>) {}
 *
 * foo(S<X>); // exact
 * foo(S<Y>); // fuzzy
 * foo(X);    // fail
 * ```
 * @see Ty.unifyWith
 */
sealed class UnifyResult {
    abstract fun merge(other: UnifyResult): UnifyResult

    fun merge(other: Substitution): UnifyResult =
        merge(Exact(other))

    abstract fun substitution(): Substitution?

    /** All types matches and trait bounds satisfied */
    data class Exact(val subst: Substitution): UnifyResult() {
        override fun merge(other: UnifyResult): UnifyResult = when (other) {
            is Failure -> Failure
            is Fuzzy -> Fuzzy(subst.merge(other.subst))
            is Exact -> Exact(subst.merge(other.subst))
        }

        override fun substitution(): Substitution? = subst
    }

    /** All types matches, but trait bounds are not satisfied */
    data class Fuzzy(val subst: Substitution): UnifyResult() {
        override fun merge(other: UnifyResult): UnifyResult = when (other) {
            is Failure -> Failure
            is Fuzzy -> Fuzzy(subst.merge(other.subst))
            is Exact -> Fuzzy(subst.merge(other.subst))
        }

        override fun substitution(): Substitution? = subst
    }

    /** Type mismatch */
    object Failure : UnifyResult() {
        override fun substitution(): Substitution? = null
        override fun merge(other: UnifyResult): UnifyResult = Failure
    }

    companion object {
        val exact: UnifyResult = Exact(emptySubstitution)
        val fuzzy: UnifyResult = Fuzzy(emptySubstitution)
        val fail: UnifyResult = Failure

        fun exactIf(condition: Boolean): UnifyResult =
            if (condition) exact else fail

        fun mergeAll(others: Iterable<UnifyResult>): UnifyResult {
            var result: UnifyResult = exact
            for (other in others) {
                result = result.merge(other)
            }
            return result
        }
    }
}

private fun Substitution.merge(other: Substitution): Substitution {
    if (isEmpty()) return other
    if (other.isEmpty()) return this

    val newSubst = toMutableMap()
    for ((param, value) in other) {
        val old = get(param)
        newSubst.put(param, if (old == null) value else getMoreCompleteTypeOld(old, value))
    }
    return newSubst
}

private fun getMoreCompleteTypeOld(ty1: Ty, ty2: Ty): Ty {
    return when (ty1) {
        is TyUnknown, is TyNever, is TyInfer -> ty2
        else -> ty1
    }
}
