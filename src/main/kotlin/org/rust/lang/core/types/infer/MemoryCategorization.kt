/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.containerExpr
import org.rust.lang.core.psi.ext.mutability
import org.rust.lang.core.types.builtinDeref
import org.rust.lang.core.types.infer.Adjustment.Deref
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.isDereference
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.stdext.nextOrNull


enum class MutabilityCategory {
    Immutable,
    /** Directly declared as mutable */
    Declared,
    /** Inherited from the fact that owner is mutable */
    Inherited;

    companion object {
        fun valueOf(mutability: Mutability): MutabilityCategory =
            when (mutability) {
                Mutability.IMMUTABLE -> MutabilityCategory.Immutable
                Mutability.MUTABLE -> MutabilityCategory.Declared
            }
    }

    fun inherit(): MutabilityCategory =
        when (this) {
            MutabilityCategory.Immutable -> MutabilityCategory.Immutable
            MutabilityCategory.Declared, MutabilityCategory.Inherited -> MutabilityCategory.Inherited
        }

    val isMutable: Boolean
        get() = when (this) {
            MutabilityCategory.Immutable -> false
            MutabilityCategory.Declared, MutabilityCategory.Inherited -> true
        }
}

/**
 * Category, MutabilityCategory, and Type
 * Currently supports only MutabilityCategory and Type
 */
class Cmt(val ty: Ty, val mutabilityCategory: MutabilityCategory? = null)

private fun processUnaryExpr(unaryExpr: RsUnaryExpr): Cmt {
    val type = unaryExpr.type
    if (!unaryExpr.isDereference) return Cmt(type)
    val base = unaryExpr.expr ?: return Cmt(type)

    val baseCmt = processExpr(base)
    return processDeref(baseCmt)
}

private fun processDotExpr(dotExpr: RsDotExpr): Cmt {
    if (dotExpr.methodCall != null) {
        return processRvalue(dotExpr)
    }
    val type = dotExpr.type
    val base = dotExpr.expr
    val baseCmt = processExpr(base)
    return Cmt(type, baseCmt.mutabilityCategory?.inherit())
}

private fun processIndexExpr(indexExpr: RsIndexExpr): Cmt {
    val type = indexExpr.type
    val base = indexExpr.containerExpr ?: return Cmt(type)
    val baseCmt = processExpr(base)
    return Cmt(type, baseCmt.mutabilityCategory?.inherit())
}

private fun processPathExpr(pathExpr: RsPathExpr): Cmt {
    val type = pathExpr.type
    val declaration = pathExpr.path.reference.resolve() ?: return Cmt(type)

    return when (declaration) {
        is RsConstant, is RsEnumVariant, is RsStructItem, is RsFunction -> Cmt(type, MutabilityCategory.Declared)

        is RsPatBinding -> {
            if (declaration.mutability.isMut) {
                Cmt(type, MutabilityCategory.Declared)
            } else {
                Cmt(type, MutabilityCategory.Immutable)
            }
        }

        is RsSelfParameter -> Cmt(type, MutabilityCategory.valueOf(declaration.mutability))

        else -> Cmt(type)
    }
}

private fun processParenExpr(parenExpr: RsParenExpr): Cmt =
    Cmt(parenExpr.type, parenExpr.expr.mutabilityCategory)

private fun processExpr(expr: RsExpr): Cmt {
    val adjustments = expr.inference?.adjustments?.get(expr) ?: emptyList()
    return processExprAdjustedWith(expr, adjustments.asReversed().iterator())
}

private fun processExprAdjustedWith(expr: RsExpr, adjustments: Iterator<Adjustment>): Cmt =
    when (adjustments.nextOrNull()) {
        is Deref -> {
            // TODO: overloaded deref
            processDeref(processExprAdjustedWith(expr, adjustments))
        }
        else -> processExprUnadjusted(expr)
    }

private fun processDeref(baseCmt: Cmt): Cmt {
    val baseType = baseCmt.ty
    val (derefType, derefMut) = baseType.builtinDeref() ?: Pair(TyUnknown, Mutability.DEFAULT_MUTABILITY)

    return when (baseType) {
        is TyReference -> Cmt(derefType, MutabilityCategory.valueOf(derefMut))
        is TyPointer -> Cmt(derefType, MutabilityCategory.valueOf(derefMut))
        else -> Cmt(derefType)
    }
}

private fun processRvalue(expr: RsExpr): Cmt =
    Cmt(expr.type, MutabilityCategory.Declared)

private fun processExprUnadjusted(expr: RsExpr): Cmt =
    when (expr) {
        is RsUnaryExpr -> processUnaryExpr(expr)
        is RsDotExpr -> processDotExpr(expr)
        is RsIndexExpr -> processIndexExpr(expr)
        is RsPathExpr -> processPathExpr(expr)
        is RsParenExpr -> processParenExpr(expr)
        else -> processRvalue(expr)
    }


val RsExpr.mutabilityCategory: MutabilityCategory?
    get() = processExpr(this).mutabilityCategory
