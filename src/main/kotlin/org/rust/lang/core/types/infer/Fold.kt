/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtInferVar
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_CT_INFER_VISITOR
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_CT_PARAMETER_VISITOR
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_RE_EARLY_BOUND_VISITOR
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_TY_INFER_VISITOR
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_TY_PLACEHOLDER_VISITOR
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_TY_PROJECTION_VISITOR
import org.rust.lang.core.types.infer.HasTypeFlagVisitor.Companion.HAS_TY_TYPE_PARAMETER_VISITOR
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.lang.utils.evaluation.tryEvaluate

interface TypeFolder {
    fun foldTy(ty: Ty): Ty = ty
    fun foldRegion(region: Region): Region = region
    fun foldConst(const: Const): Const = const
}

interface TypeVisitor {
    fun visitTy(ty: Ty): Boolean = false
    fun visitRegion(region: Region): Boolean = false
    fun visitConst(const: Const): Boolean = false
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

/** Deeply replace a [TyPlaceholder] with a fresh [TyInfer] */
fun <T> TypeFoldable<T>.foldTyPlaceholderWithTyInfer(): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty {
            val foldedTy = if (ty is TyPlaceholder) TyInfer.TyVar(ty.origin) else ty
            return if (foldedTy.hasTyPlaceholder) foldedTy.superFoldWith(this) else foldedTy
        }
    })

/** Deeply replace any [CtInferVar] with the function [folder] */
fun <T : TypeFoldable<T>> TypeFoldable<T>.foldCtInferWith(folder: (CtInferVar) -> Const): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty = if (ty.hasCtInfer) ty.superFoldWith(this) else ty
        override fun foldConst(const: Const): Const {
            val foldedCt = if (const is CtInferVar) folder(const) else const
            return if (foldedCt.hasCtInfer) foldedCt.superFoldWith(this) else foldedCt
        }
    }).tryEvaluate()

/** Deeply replace any [CtConstParameter] with the function [folder] */
fun <T : TypeFoldable<T>> TypeFoldable<T>.foldCtConstParameterWith(folder: (CtConstParameter) -> Const): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty = if (ty.hasCtConstParameters) ty.superFoldWith(this) else ty
        override fun foldConst(const: Const): Const = when {
            const is CtConstParameter -> folder(const)
            const.hasCtConstParameters -> const.superFoldWith(this)
            else -> const
        }
    }).tryEvaluate()

/** Deeply replace any [TyProjection] with the function [folder] */
fun <T> TypeFoldable<T>.foldTyProjectionWith(folder: (TyProjection) -> Ty): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty = when {
            ty is TyProjection -> {
                val foldedTy = if (ty.type.hasTyProjection || ty.trait.hasTyProjection) {
                    ty.superFoldWith(this) as TyProjection
                } else {
                    ty
                }
                folder(foldedTy)
            }
            ty.hasTyProjection -> ty.superFoldWith(this)
            else -> ty
        }
    })

/**
 * Deeply replace any [TyTypeParameter], [ReEarlyBound] and [CtConstParameter] by [subst] mapping.
 */
fun <T : TypeFoldable<T>> TypeFoldable<T>.substitute(subst: Substitution): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty = when {
            ty is TyTypeParameter -> subst[ty] ?: ty
            ty.needsSubst -> ty.superFoldWith(this)
            else -> ty
        }

        override fun foldRegion(region: Region): Region =
            (region as? ReEarlyBound)?.let { subst[it] } ?: region

        override fun foldConst(const: Const): Const = when {
            const is CtConstParameter -> subst[const] ?: const
            const.hasCtConstParameters -> const.superFoldWith(this)
            else -> const
        }
    }).tryEvaluate()

fun <T : TypeFoldable<T>> TypeFoldable<T>.substituteOrUnknown(subst: Substitution): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty = when {
            ty is TyTypeParameter -> subst[ty] ?: TyUnknown
            ty.needsSubst -> ty.superFoldWith(this)
            else -> ty
        }

        override fun foldRegion(region: Region): Region =
            (region as? ReEarlyBound)?.let { subst[it] } ?: ReUnknown

        override fun foldConst(const: Const): Const = when {
            const is CtConstParameter -> subst[const] ?: CtUnknown
            const.hasCtConstParameters -> const.superFoldWith(this)
            else -> const
        }
    }).tryEvaluate()

fun <T> TypeFoldable<T>.containsTyOfClass(classes: List<Class<*>>): Boolean =
    visitWith(object : TypeVisitor {
        override fun visitTy(ty: Ty): Boolean =
            if (classes.any { it.isInstance(ty) }) true else ty.superVisitWith(this)
    })

fun <T> TypeFoldable<T>.containsTyOfClass(vararg classes: Class<*>): Boolean =
    containsTyOfClass(classes.toList())

fun <T> TypeFoldable<T>.containsConstOfClass(classes: List<Class<*>>): Boolean =
    visitWith(object : TypeVisitor {
        override fun visitTy(ty: Ty): Boolean = ty.superVisitWith(this)
        override fun visitConst(const: Const): Boolean = classes.any { it.isInstance(const) }
    })

fun <T> TypeFoldable<T>.containsConstOfClass(vararg classes: Class<*>): Boolean =
    containsConstOfClass(classes.toList())

fun <T> TypeFoldable<T>.collectInferTys(): List<TyInfer> {
    val list = mutableListOf<TyInfer>()
    visitInferTys {
        list.add(it)
        false
    }
    return list
}

fun <T> TypeFoldable<T>.visitInferTys(visitor: (TyInfer) -> Boolean): Boolean {
    return visitWith(object : TypeVisitor {
        override fun visitTy(ty: Ty): Boolean = when {
            ty is TyInfer -> visitor(ty)
            ty.hasTyInfer -> ty.superVisitWith(this)
            else -> false
        }
    })
}

fun <T> TypeFoldable<T>.visitTypeParameters(visitor: (TyTypeParameter) -> Boolean): Boolean {
    return visitWith(object : TypeVisitor {
        override fun visitTy(ty: Ty): Boolean = when {
            ty is TyTypeParameter -> visitor(ty)
            ty.hasTyTypeParameters -> ty.superVisitWith(this)
            else -> false
        }
    })
}

private data class HasTypeFlagVisitor(val mask: TypeFlags) : TypeVisitor {
    override fun visitTy(ty: Ty): Boolean = ty.flags.and(mask) != 0
    override fun visitRegion(region: Region): Boolean = region.flags.and(mask) != 0
    override fun visitConst(const: Const): Boolean = const.flags.and(mask) != 0

    companion object {
        val HAS_TY_INFER_VISITOR = HasTypeFlagVisitor(HAS_TY_INFER_MASK)
        val HAS_TY_TYPE_PARAMETER_VISITOR = HasTypeFlagVisitor(HAS_TY_TYPE_PARAMETER_MASK)
        val HAS_TY_PROJECTION_VISITOR = HasTypeFlagVisitor(HAS_TY_PROJECTION_MASK)
        val HAS_RE_EARLY_BOUND_VISITOR = HasTypeFlagVisitor(HAS_RE_EARLY_BOUND_MASK)
        val HAS_CT_INFER_VISITOR = HasTypeFlagVisitor(HAS_CT_INFER_MASK)
        val HAS_CT_PARAMETER_VISITOR = HasTypeFlagVisitor(HAS_CT_PARAMETER_MASK)
        val HAS_TY_PLACEHOLDER_VISITOR = HasTypeFlagVisitor(HAS_TY_PLACEHOLDER_MASK)

        val NEEDS_INFER = HasTypeFlagVisitor(HAS_TY_INFER_MASK or HAS_CT_INFER_MASK)
        val NEEDS_EVAL = HasTypeFlagVisitor(HAS_CT_UNEVALUATED_MASK or HAS_CT_PARAMETER_MASK)
        val NEEDS_SUBST = HasTypeFlagVisitor(
            HAS_TY_TYPE_PARAMETER_MASK
                or HAS_RE_EARLY_BOUND_MASK
                or HAS_CT_PARAMETER_MASK
        )
        /** `true` if there are "names" of types and regions and so forth that are local to a particular fn */
        val HAS_FREE_LOCAL_NAMES = HasTypeFlagVisitor(
            HAS_TY_TYPE_PARAMETER_MASK
                or HAS_CT_PARAMETER_MASK
                or HAS_TY_INFER_MASK
                or HAS_CT_INFER_MASK
                or HAS_TY_OPAQUE_MASK
        )
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

val TypeFoldable<*>.hasCtInfer
    get(): Boolean = visitWith(HAS_CT_INFER_VISITOR)

val TypeFoldable<*>.hasCtConstParameters
    get(): Boolean = visitWith(HAS_CT_PARAMETER_VISITOR)

val TypeFoldable<*>.hasTyPlaceholder
    get(): Boolean = visitWith(HAS_TY_PLACEHOLDER_VISITOR)

val TypeFoldable<*>.needsInfer
    get(): Boolean = visitWith(HasTypeFlagVisitor.NEEDS_INFER)

val TypeFoldable<*>.needsSubst
    get(): Boolean = visitWith(HasTypeFlagVisitor.NEEDS_SUBST)

val TypeFoldable<*>.needsEval
    get(): Boolean = visitWith(HasTypeFlagVisitor.NEEDS_EVAL)

/**
 * Indicates whether this value references only 'global' generic parameters that are the same regardless
 * of what fn we are in. This is used for caching.
 */
val TypeFoldable<*>.isGlobal
    get(): Boolean = !visitWith(HasTypeFlagVisitor.HAS_FREE_LOCAL_NAMES)
