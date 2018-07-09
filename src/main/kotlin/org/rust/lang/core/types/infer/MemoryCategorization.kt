/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.TyPointer
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type


abstract class PointerKind(val mutability: Mutability)
class BorrowedPointer(mutability: Mutability) : PointerKind(mutability)
class UnsafePointer(mutability: Mutability) : PointerKind(mutability)


enum class MutabilityCategory {
    Immutable, Declared, Inherited;

    companion object {
        fun valueOf(mutability: Mutability): MutabilityCategory {
            return when (mutability) {
                Mutability.IMMUTABLE -> MutabilityCategory.Immutable
                Mutability.MUTABLE -> MutabilityCategory.Declared
            }
        }
    }

    fun inherit(): MutabilityCategory {
        return when (this) {
            MutabilityCategory.Immutable -> MutabilityCategory.Immutable
            MutabilityCategory.Declared, MutabilityCategory.Inherited -> MutabilityCategory.Inherited
        }
    }

    val isMutable: Boolean get() {
        return when (this) {
            MutabilityCategory.Immutable -> false
            MutabilityCategory.Declared, MutabilityCategory.Inherited -> true
        }
    }

    val isImmutable: Boolean get() = !isMutable
}

val RsExpr.mutabilityCategory: MutabilityCategory? get() {
    return when (this) {
        is RsUnaryExpr -> {
            val expr = expr ?: return null

            if (mul != null) {
                val type = expr.type
                val pointer = when (type) {
                    is TyPointer -> UnsafePointer(type.mutability)
                    is TyReference -> BorrowedPointer(type.mutability)
                    else -> return null
                }

                MutabilityCategory.valueOf(pointer.mutability)
            }
            else null
        }

        is RsDotExpr -> {
            val type = expr.type
            val isDeref = expr.inference?.adjustments?.get(this)?.any { it.kind == Adjust.DEREF } ?: false

            if (isDeref && type is TyReference)
                MutabilityCategory.valueOf(type.mutability)
            else
                expr.mutabilityCategory?.inherit()
        }

        is RsIndexExpr -> {
            this.containerExpr?.mutabilityCategory?.inherit()
        }

        is RsPathExpr -> {
            val declaration = path.reference.resolve() ?: return null

            // this brings false-negative, because such variable should has Immutable category:
            // let x; x = 1;
            // x = 2; <-- `x` is immutable, but it determined as mutable
            //
            // so it would be replaced with some kind of data-flow analysis
            val letExpr = declaration.ancestorStrict<RsLetDecl>()
            if (letExpr != null && letExpr.eq == null) return MutabilityCategory.Declared

            when (declaration) {
                is RsConstant, is RsEnumVariant, is RsStructItem, is RsFunction -> return MutabilityCategory.Declared

                is RsPatBinding -> {
                    return if (declaration.kind is BindByValue && declaration.mutability.isMut)
                        MutabilityCategory.Declared
                    else MutabilityCategory.Immutable
                }

                is RsSelfParameter -> return MutabilityCategory.valueOf(declaration.mutability)

                else -> null
            }
        }

        is RsParenExpr -> expr.mutabilityCategory

        else -> null
    }
}
