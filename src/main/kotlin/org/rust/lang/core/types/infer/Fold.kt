/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import com.intellij.util.BitUtil
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_RE_EARLY_BOUND_VISITOR
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_TY_INFER_VISITOR
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_TY_PROJECTION_VISITOR
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_TY_TYPE_PARAMETER_VISITOR
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*

interface TypeFolder {
    fun foldTy(ty: Ty): Ty = ty
    fun foldRegion(region: Region): Region = region
}

interface TypeVisitor {
    fun visitTy(ty: Ty): Boolean = false
    fun visitRegion(region: Region): Boolean = false
}

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
        override fun foldTy(ty: Ty): Ty {
            val foldedTy = if (ty is TyInfer) folder(ty) else ty
            return if (foldedTy.hasTyInfer) foldedTy.superFoldWith(this) else foldedTy
        }
    })

/** Deeply replace any [TyTypeParameter] with the function [folder] */
fun <T> TypeFoldable<T>.foldTyTypeParameterWith(folder: (TyTypeParameter) -> Ty): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty = when {
            ty is TyTypeParameter -> folder(ty)
            ty.hasTyTypeParameters -> ty.superFoldWith(this)
            else -> ty
        }
    })

/** Deeply replace any [TyProjection] with the function [folder] */
fun <T> TypeFoldable<T>.foldTyProjectionWith(folder: (TyProjection) -> Ty): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty = when {
            ty is TyProjection -> folder(ty)
            ty.hasTyProjection -> ty.superFoldWith(this)
            else -> ty
        }
    })

/**
 * Deeply replace any [TyTypeParameter] by [subst] mapping.
 */
fun <T> TypeFoldable<T>.substitute(subst: Substitution): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty = when {
            ty is TyTypeParameter -> subst[ty] ?: ty
            ty.needToSubstitute -> ty.superFoldWith(this)
            else -> ty
        }

        override fun foldRegion(region: Region): Region =
            (region as? ReEarlyBound)?.let { subst[it] } ?: region
    })

fun <T> TypeFoldable<T>.substituteOrUnknown(subst: Substitution): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty = when {
            ty is TyTypeParameter -> subst[ty] ?: TyUnknown
            ty.needToSubstitute -> ty.superFoldWith(this)
            else -> ty
        }

        override fun foldRegion(region: Region): Region =
            (region as? ReEarlyBound)?.let { subst[it] } ?: region
    })

fun <T> TypeFoldable<T>.containsTyOfClass(classes: List<Class<*>>): Boolean =
    visitWith(object : TypeVisitor {
        override fun visitTy(ty: Ty): Boolean =
            if (classes.any { it.isInstance(ty) }) true else ty.superVisitWith(this)
    })

fun <T> TypeFoldable<T>.containsTyOfClass(vararg classes: Class<*>): Boolean =
    containsTyOfClass(classes.toList())

fun <T> TypeFoldable<T>.collectInferTys(): List<TyInfer> {
    val list = mutableListOf<TyInfer>()
    visitWith(object : TypeVisitor {
        override fun visitTy(ty: Ty): Boolean = when {
            ty is TyInfer -> {
                list.add(ty)
                false
            }
            ty.hasTyInfer -> ty.superVisitWith(this)
            else -> false
        }
    })
    return list
}

private data class HasTypeFlagVisitor(val flag: TypeFlags) : TypeVisitor {
    override fun visitTy(ty: Ty): Boolean = BitUtil.isSet(ty.flags, flag)
    override fun visitRegion(region: Region): Boolean = BitUtil.isSet(region.flags, flag)

    companion object {
        val HAS_TY_INFER_VISITOR = HasTypeFlagVisitor(HAS_TY_INFER_MASK)
        val HAS_TY_TYPE_PARAMETER_VISITOR = HasTypeFlagVisitor(HAS_TY_TYPE_PARAMETER_MASK)
        val HAS_TY_PROJECTION_VISITOR = HasTypeFlagVisitor(HAS_TY_PROJECTION_MASK)
        val HAS_RE_EARLY_BOUND_VISITOR = HasTypeFlagVisitor(HAS_RE_EARLY_BOUND_MASK)
    }
}

val TypeFoldable<*>.hasTyInfer
    get(): Boolean = visitWith(HAS_TY_INFER_VISITOR)

val TypeFoldable<*>.hasTyTypeParameters
    get(): Boolean = visitWith(HAS_TY_TYPE_PARAMETER_VISITOR)

val TypeFoldable<*>.hasTyProjection
    get(): Boolean = visitWith(HAS_TY_PROJECTION_VISITOR)

val TypeFoldable<*>.hasReEarlyBounds
    get(): Boolean = visitWith(HAS_RE_EARLY_BOUND_VISITOR)

val TypeFoldable<*>.needToSubstitute
    get(): Boolean = hasTyTypeParameters || hasReEarlyBounds
