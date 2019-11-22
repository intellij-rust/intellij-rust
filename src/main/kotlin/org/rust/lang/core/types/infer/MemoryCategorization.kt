/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.infer.Aliasability.FreelyAliasable
import org.rust.lang.core.types.infer.Aliasability.NonAliasable
import org.rust.lang.core.types.infer.AliasableReason.*
import org.rust.lang.core.types.infer.BorrowKind.ImmutableBorrow
import org.rust.lang.core.types.infer.BorrowKind.MutableBorrow
import org.rust.lang.core.types.infer.Categorization.*
import org.rust.lang.core.types.infer.ImmutabilityBlame.*
import org.rust.lang.core.types.infer.MutabilityCategory.Declared
import org.rust.lang.core.types.infer.PointerKind.BorrowedPointer
import org.rust.lang.core.types.infer.PointerKind.UnsafePointer
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.stdext.nextOrNull

/** [Categorization] is a subset of the full expression forms */
sealed class Categorization {
    /** Temporary value */
    data class Rvalue(val region: Region) : Categorization()

    /** Static value */
    object StaticItem : Categorization()

    /** Local variable */
    data class Local(val declaration: RsElement) : Categorization()

    /** Dereference of a pointer */
    data class Deref(val cmt: Cmt, val pointerKind: PointerKind) : Categorization() {
        fun unwrapDerefs(): Cmt =
            when (cmt.category) {
                is Deref -> cmt.category.unwrapDerefs()
                else -> cmt
            }
    }

    /** Something reachable from the base without a pointer dereference (e.g. field) */
    sealed class Interior : Categorization() {
        abstract val cmt: Cmt

        /** e.g. `s.field` */
        data class Field(override val cmt: Cmt, val name: String?) : Interior()

        /** e.g. `arr[0]` */
        data class Index(override val cmt: Cmt) : Interior()

        /** e.g. `fn foo([_, a, _, _]: [A; 4]) { ... }` */
        data class Pattern(override val cmt: Cmt) : Interior()
    }

    /** Selects a particular enum variant (if enum has more than one variant) */
    data class Downcast(val cmt: Cmt, val element: RsElement) : Categorization()
}

sealed class BorrowKind {
    object ImmutableBorrow : BorrowKind()
    object MutableBorrow : BorrowKind()

    companion object {
        fun from(mutability: Mutability): BorrowKind =
            when (mutability) {
                Mutability.IMMUTABLE -> ImmutableBorrow
                Mutability.MUTABLE -> MutableBorrow
            }

        fun isCompatible(firstKind: BorrowKind, secondKind: BorrowKind): Boolean =
            firstKind == ImmutableBorrow && secondKind == ImmutableBorrow
    }
}

sealed class PointerKind {
    data class BorrowedPointer(val borrowKind: BorrowKind, val region: Region) : PointerKind()
    data class UnsafePointer(val mutability: Mutability) : PointerKind()
}

/** Reason why something is immutable */
sealed class ImmutabilityBlame {
    /** Immutable as immutable variable */
    class LocalDeref(val element: RsElement) : ImmutabilityBlame()

    /** Immutable as dereference of immutable variable */
    object AdtFieldDeref : ImmutabilityBlame()

    /** Immutable as interior of immutable */
    class ImmutableLocal(val element: RsElement) : ImmutabilityBlame()
}

/**
 * Borrow checker have to never permit &mut-borrows of aliasable data.
 * "Rules" of aliasability:
 * - Local variables are never aliasable as they are accessible only within the stack frame;
 * - Owned content is aliasable if it is found in an aliasable location;
 * - `&T` is aliasable, and hence can only be borrowed immutably;
 * - `&mut T` is aliasable if `T` is aliasable
 */
sealed class Aliasability {
    class FreelyAliasable(val reason: AliasableReason) : Aliasability()
    object NonAliasable : Aliasability()
}

enum class AliasableReason {
    Borrowed,
    Static,
    StaticMut
}

/** Mutability of the expression address */
enum class MutabilityCategory {
    /** Any immutable */
    Immutable,
    /** Directly declared as mutable */
    Declared,
    /** Inherited from the fact that owner is mutable */
    Inherited;

    companion object {
        fun from(mutability: Mutability): MutabilityCategory =
            when (mutability) {
                Mutability.IMMUTABLE -> Immutable
                Mutability.MUTABLE -> Declared
            }

        fun from(borrowKind: BorrowKind): MutabilityCategory =
            when (borrowKind) {
                is ImmutableBorrow -> Immutable
                is MutableBorrow -> Declared
            }

        fun from(pointerKind: PointerKind): MutabilityCategory =
            when (pointerKind) {
                is BorrowedPointer -> from(pointerKind.borrowKind)
                is UnsafePointer -> from(pointerKind.mutability)
            }
    }

    fun inherit(): MutabilityCategory =
        when (this) {
            Immutable -> Immutable
            Declared, Inherited -> Inherited
        }

    val isMutable: Boolean get() = this != Immutable
}

/**
 * [Cmt]: Category, MutabilityCategory, and Type
 *
 * Imagine a routine Address(Expr) that evaluates an expression and returns an
 * address where the result is to be found.  If Expr is a place, then this
 * is the address of the place.  If Expr is an rvalue, this is the address of
 * some temporary spot in memory where the result is stored.
 *
 * [element]: Expr
 * [category]: kind of Expr
 * [mutabilityCategory]: mutability of Address(Expr)
 * [ty]: the type of data found at Address(Expr)
 */
class Cmt(
    val element: RsElement,
    val category: Categorization? = null,
    val mutabilityCategory: MutabilityCategory = MutabilityCategory.from(Mutability.DEFAULT_MUTABILITY),
    val ty: Ty
) {
    val immutabilityBlame: ImmutabilityBlame?
        get() = when (category) {
            is Deref -> {
                // try to figure out where the immutable reference came from
                val pointerKind = category.pointerKind
                val baseCmt = category.cmt
                if (pointerKind is BorrowedPointer && pointerKind.borrowKind is ImmutableBorrow) {
                    when (baseCmt.category) {
                        is Local -> LocalDeref(baseCmt.category.declaration)
                        is Interior -> AdtFieldDeref
                        else -> null
                    }
                } else if (pointerKind is UnsafePointer) {
                    null
                } else {
                    baseCmt.immutabilityBlame
                }
            }
            is Local -> ImmutableLocal(category.declaration)
            is Interior -> category.cmt.immutabilityBlame
            is Downcast -> category.cmt.immutabilityBlame
            else -> null
        }

    val isMutable: Boolean get() = mutabilityCategory.isMutable

    val aliasability: Aliasability
        get() = when {
            category is Deref && category.pointerKind is BorrowedPointer ->
                when (category.pointerKind.borrowKind) {
                    is MutableBorrow -> category.cmt.aliasability
                    is ImmutableBorrow -> FreelyAliasable(Borrowed)
                }
            category is StaticItem -> FreelyAliasable(if (isMutable) StaticMut else Static)
            category is Interior -> category.cmt.aliasability
            category is Downcast -> category.cmt.aliasability
            else -> NonAliasable
        }
}

class MemoryCategorizationContext(val lookup: ImplLookup, val inference: RsInferenceData) {
    fun processExpr(expr: RsExpr): Cmt {
        val adjustments = inference.getExprAdjustments(expr)
        return processExprAdjustedWith(expr, adjustments.asReversed().iterator())
    }

    private fun processExprAdjustedWith(expr: RsExpr, adjustments: Iterator<Adjustment>): Cmt {
        return when (val adjustment = adjustments.nextOrNull()) {
            is Adjustment.Deref -> {
                // TODO: overloaded deref
                processDeref(expr, processExprAdjustedWith(expr, adjustments))
            }
            is Adjustment.BorrowReference -> {
                val target = adjustment.target
                val unadjustedCmt = processExprUnadjusted(expr)
                if (target.mutability.isMut && !unadjustedCmt.isMutable) {
                    // ignore inconsistent adjustment
                    processExprUnadjusted(expr)
                } else {
                    processRvalue(expr, target)
                }
            }
            else -> processExprUnadjusted(expr)
        }
    }

    private fun processExprUnadjusted(expr: RsExpr): Cmt =
        when (expr) {
            is RsUnaryExpr -> processUnaryExpr(expr)
            is RsDotExpr -> processDotExpr(expr)
            is RsIndexExpr -> processIndexExpr(expr)
            is RsPathExpr -> processPathExpr(expr)
            is RsParenExpr -> processParenExpr(expr)
            else -> processRvalue(expr)
        }

    private fun processUnaryExpr(unaryExpr: RsUnaryExpr): Cmt {
        if (!unaryExpr.isDereference) return processRvalue(unaryExpr)
        val base = unaryExpr.expr ?: return Cmt(unaryExpr, ty = inference.getExprType(unaryExpr))
        val baseCmt = processExpr(base)
        return processDeref(unaryExpr, baseCmt)
    }

    private fun processDotExpr(dotExpr: RsDotExpr): Cmt {
        if (dotExpr.methodCall != null) return processRvalue(dotExpr)
        val type = inference.getExprType(dotExpr)
        val base = dotExpr.expr
        val baseCmt = processExpr(base)
        val fieldName = dotExpr.fieldLookup?.identifier?.text ?: dotExpr.fieldLookup?.integerLiteral?.text
        return cmtOfField(dotExpr, baseCmt, fieldName, type)
    }

    private fun processIndexExpr(indexExpr: RsIndexExpr): Cmt {
        val type = inference.getExprType(indexExpr)
        val base = indexExpr.containerExpr ?: return Cmt(indexExpr, ty = type)
        val baseCmt = processExpr(base)
        return Cmt(indexExpr, Interior.Index(baseCmt), baseCmt.mutabilityCategory.inherit(), type)
    }

    private fun processPathExpr(pathExpr: RsPathExpr): Cmt {
        val type = inference.getExprType(pathExpr)
        val declaration = inference.getResolvedPath(pathExpr).singleOrNull()?.element ?: return Cmt(pathExpr, ty = type)
        return when (declaration) {
            is RsConstant -> {
                if (!declaration.isConst) {
                    Cmt(pathExpr, StaticItem, MutabilityCategory.from(declaration.mutability), type)
                } else {
                    processRvalue(pathExpr)
                }
            }

            is RsEnumVariant, is RsStructItem, is RsFunction -> processRvalue(pathExpr)

            is RsPatBinding -> Cmt(pathExpr, Local(declaration), MutabilityCategory.from(declaration.mutability), type)

            is RsSelfParameter -> Cmt(pathExpr, Local(declaration), MutabilityCategory.from(declaration.mutability), type)

            else -> Cmt(pathExpr, ty = type)
        }
    }

    private fun processParenExpr(parenExpr: RsParenExpr): Cmt =
        processExpr(parenExpr.expr)

    private fun processDeref(expr: RsExpr, baseCmt: Cmt): Cmt {
        val baseType = baseCmt.ty
        val (derefType, derefMut) = baseType.builtinDeref() ?: Pair(TyUnknown, Mutability.DEFAULT_MUTABILITY)

        val pointerKind = when (baseType) {
            is TyReference -> BorrowedPointer(BorrowKind.from(baseType.mutability), baseType.region)
            is TyPointer -> UnsafePointer(baseType.mutability)
            else -> UnsafePointer(derefMut)
        }

        return Cmt(expr, Deref(baseCmt, pointerKind), MutabilityCategory.from(pointerKind), derefType)
    }

    // `rvalue_promotable_map` is needed to distinguish rvalues with static region and rvalue with temporary region,
    // so now all rvalues have static region
    fun processRvalue(expr: RsExpr, ty: Ty = inference.getExprType(expr)): Cmt =
        Cmt(expr, Rvalue(ReStatic), Declared, ty)

    fun processRvalue(element: RsElement, tempScope: Region, ty: Ty): Cmt =
        Cmt(element, Rvalue(tempScope), Declared, ty)

    fun walkPat(cmt: Cmt, pat: RsPat, callback: (Cmt, RsPat, RsPatBinding) -> Unit) {
        fun processTuplePats(pats: List<RsPat>) {
            for ((index, subPat) in pats.withIndex()) {
                val subBinding = subPat.descendantsOfType<RsPatBinding>().firstOrNull() ?: continue
                val subType = inference.getBindingType(subBinding)
                val subCmt = Cmt(pat, Interior.Field(cmt, index.toString()), cmt.mutabilityCategory.inherit(), subType)
                walkPat(subCmt, subPat, callback)
            }
        }

        when (pat) {
            is RsPatIdent -> {
                val binding = pat.patBinding
                if (binding.reference.resolve()?.isConstantLike != true) {
                    callback(cmt, pat, binding)
                }
                if (pat.patBinding.reference.resolve() !is RsEnumVariant) {
                    pat.pat?.let { walkPat(cmt, it, callback) }
                }
            }

            is RsPatTupleStruct -> processTuplePats(pat.patList)

            is RsPatTup -> processTuplePats(pat.patList)

            is RsPatStruct -> {
                for (patField in pat.patFieldList) {
                    val binding = patField.patBinding
                    if (binding != null) {
                        val fieldName = binding.referenceName
                        val fieldType = inference.getBindingType(binding)
                        val fieldCmt = cmtOfField(pat, cmt, fieldName, fieldType)
                        callback(fieldCmt, pat, binding)
                    } else {
                        val patFieldFull = patField.patFieldFull ?: continue
                        val fieldName = patFieldFull.referenceName
                        val fieldPat = patFieldFull.pat
                        val fieldType = inference.getPatType(fieldPat)
                        val fieldCmt = cmtOfField(pat, cmt, fieldName, fieldType)
                        walkPat(fieldCmt, fieldPat, callback)
                    }
                }
            }

            is RsPatSlice -> {
                val elementCmt = cmtOfSliceElement(pat, cmt)
                pat.patList.forEach { walkPat(elementCmt, it, callback) }
            }
        }
    }

    private fun cmtOfField(element: RsElement, baseCmt: Cmt, fieldName: String?, fieldType: Ty): Cmt =
        Cmt(
            element,
            Interior.Field(baseCmt, fieldName),
            baseCmt.mutabilityCategory.inherit(),
            fieldType
        )

    private fun cmtOfSliceElement(element: RsElement, baseCmt: Cmt): Cmt =
        Cmt(
            element,
            Interior.Pattern(baseCmt),
            baseCmt.mutabilityCategory.inherit(),
            baseCmt.ty
        )
}
