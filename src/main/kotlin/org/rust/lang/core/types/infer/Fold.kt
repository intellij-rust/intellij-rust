/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.types.ty.Substitution
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyInfer
import org.rust.lang.core.types.ty.TyTypeParameter

typealias TypeFolder = (Ty) -> Ty

interface TypeFoldable<out Self> {
    /**
     * Fold `this` type with the folder.
     *
     * This works for:
     * ```
     *     A.foldWith { C } == C
     *     A<B>.foldWith { C } == C
     * ```
     *
     * `a.foldWith(folder)` is equivalent to `folder(a)` in cases where `a` is `Ty`.
     * In other cases the call delegates to [superFoldWith]
     *
     * The folding basically is not deep. If you want to fold type deeply, you should write a folder
     * somehow like this:
     * ```kotlin
     * // We initially have `ty = A<B<C>, B<C>>` and want replace C to D to get `A<B<D>, B<D>>`
     * ty.foldWith(object : TypeFolder {
     *     override fun invoke(ty: Ty): Ty =
     *         if (it == C) D else it.superFoldWith(this)
     * })
     * ```
     */
    fun foldWith(folder: TypeFolder): Self = superFoldWith(folder)

    /**
     * Fold inner types (not this type) with the folder.
     * `A<A<B>>.foldWith { C } == A<C>`
     * This method should be used only by a folder implementations internally
     */
    fun superFoldWith(folder: TypeFolder): Self
}

/** Deeply replace any [TyInfer] with the function [folder] */
fun <T> TypeFoldable<T>.foldTyInferWith(folder: (TyInfer) -> Ty): T =
    foldWith(object : TypeFolder {
        override fun invoke(ty: Ty): Ty =
            (if (ty is TyInfer) folder(ty) else ty).superFoldWith(this)
    })

/** Deeply replace any [TyTypeParameter] with the function [folder] */
fun <T> TypeFoldable<T>.foldTyTypeParameterWith(folder: (TyTypeParameter) -> Ty): T =
    foldWith(object : TypeFolder {
        override fun invoke(ty: Ty): Ty =
            if (ty is TyTypeParameter) folder(ty) else ty.superFoldWith(this)
    })

/**
 * Deeply replace any [TyTypeParameter] by [subst] mapping.
 * It differs from [substitute] in handling of TyTypeParameter where it can't be substituted
 * TODO remove it
 */
fun <T> TypeFoldable<T>.foldWithSubst(subst: Substitution): T =
    foldWith(object : TypeFolder {
        override fun invoke(ty: Ty): Ty =
            subst[ty] ?: ty.superFoldWith(this)
    })

/**
 * Deeply replace any [TyTypeParameter] by [subst] mapping.
 * This is a plain old `Ty.substitute()`. It will be completely replaced with folding alternatives soon
 * It differs from [foldWithSubst] in handling of TyTypeParameter where it can't be substituted
 * TODO remove it
 */
fun <T> TypeFoldable<T>.substitute(subst: Substitution): T =
    foldWith(object : TypeFolder {
        override fun invoke(ty: Ty): Ty =
            if (ty is TyTypeParameter) ty.substituteOld(subst) else ty.superFoldWith(this)
    })
