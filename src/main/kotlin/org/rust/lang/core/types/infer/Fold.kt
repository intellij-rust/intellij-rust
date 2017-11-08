/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.types.ty.*

typealias TypeFolder = (Ty) -> Ty
typealias TypeVisitor = (Ty) -> Boolean

/**
 * Despite a scary name, [TypeFoldable] is a rather simple thing.
 *
 * It allows to map type variables within a type (or another object,
 * containing a type, like a [Predicate]) to other types.
 */
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
     * This method should be used only by a folder implementations internally.
     */
    fun superFoldWith(folder: TypeFolder): Self

    /** Similar to [superVisitWith], but just visit types without folding */
    fun visitWith(visitor: TypeVisitor): Boolean = superVisitWith(visitor)

    /** Similar to [foldWith], but just visit types without folding */
    fun superVisitWith(visitor: TypeVisitor): Boolean
}

/** Deeply replace any [TyInfer] with the function [folder] */
fun <T> TypeFoldable<T>.foldTyInferWith(folder: (TyInfer) -> Ty): T =
    foldWith(object : TypeFolder {
        override fun invoke(ty: Ty): Ty {
            val foldedTy = if (ty is TyInfer) folder(ty) else ty
            return if (foldedTy.hasTyInfer) foldedTy.superFoldWith(this) else foldedTy
        }
    })

/** Deeply replace any [TyTypeParameter] with the function [folder] */
fun <T> TypeFoldable<T>.foldTyTypeParameterWith(folder: (TyTypeParameter) -> Ty): T =
    foldWith(object : TypeFolder {
        override fun invoke(ty: Ty): Ty = when {
            ty is TyTypeParameter -> folder(ty)
            ty.hasTyTypeParameters -> ty.superFoldWith(this)
            else -> ty
        }
    })

/**
 * Deeply replace any [TyTypeParameter] by [subst] mapping.
 */
fun <T> TypeFoldable<T>.substitute(subst: Substitution): T =
    foldWith(object : TypeFolder {
        override fun invoke(ty: Ty): Ty =
            subst[ty] ?: if (ty.hasTyTypeParameters) ty.superFoldWith(this) else ty
    })

fun <T> TypeFoldable<T>.containsTyOfClass(classes: List<Class<*>>): Boolean =
    visitWith(object : TypeVisitor {
        override fun invoke(ty: Ty): Boolean =
            if (classes.any { it.isInstance(ty) }) true else ty.superVisitWith(this)
    })
