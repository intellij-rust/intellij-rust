/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.infer.RsInferenceResult
import org.rust.lang.core.types.infer.inferTypeReferenceType
import org.rust.lang.core.types.infer.inferTypesIn
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.recursionGuard


val RsTypeReference.type: Ty
    get() = recursionGuard(this, Computable { inferTypeReferenceType(this) })
        ?: TyUnknown

val RsTypeElement.lifetimeElidable: Boolean get() {
    val typeOwner = owner.parent
    return typeOwner !is RsFieldDecl && typeOwner !is RsTupleFieldDecl && typeOwner !is RsTypeAlias
}

private val TYPE_INFERENCE_KEY: Key<CachedValue<RsInferenceResult>> = Key.create("TYPE_INFERENCE_KEY")

val RsInferenceContextOwner.inference: RsInferenceResult
    get() = CachedValuesManager.getCachedValue(this, TYPE_INFERENCE_KEY) {
        val inferred = inferTypesIn(this)
        val project = project

        // CachedValueProvider.Result can accept a ModificationTracker as a dependency, so the
        // cached value will be invalidated if the modification counter is incremented.
        if (this is RsModificationTrackerOwner) {
            Result.create(inferred, project.rustStructureModificationTracker, modificationTracker)
        } else {
            Result.create(inferred, project.rustStructureModificationTracker)
        }
    }

val PsiElement.inference: RsInferenceResult?
    get() = contextOrSelf<RsInferenceContextOwner>()?.inference

val RsPatBinding.type: Ty
    get() = inference?.getBindingType(this) ?: TyUnknown

val RsExpr.type: Ty
    get() = inference?.getExprType(this) ?: TyUnknown

val RsExpr.declaration: RsElement?
    get() = when (this) {
        is RsPathExpr -> path.reference.resolve()
        is RsCallExpr -> expr.declaration
        is RsStructLiteral -> path.reference.resolve()
        else -> null
    }

val RsTraitOrImpl.selfType: Ty get() {
    return when (this) {
        is RsImplItem -> typeReference?.type ?: return TyUnknown
        is RsTraitItem -> TyTypeParameter.self(this)
        else -> error("Unreachable")
    }
}

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

abstract class PointerKind(val mutability: Mutability)
class BorrowedPointer(mutability: Mutability) : PointerKind(mutability)
class UnsafePointer(mutability: Mutability) : PointerKind(mutability)

val RsExpr.isDereference: Boolean get() = this is RsUnaryExpr && this.mul != null

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
                // expr.mutabilityCategory
            }
            else null
        }

        is RsDotExpr -> {
            expr.mutabilityCategory?.inherit()
        }

        // is RsIndexExpr -> {}

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

        else -> null
    }
}

private val DEFAULT_MUTABILITY = true

val RsExpr.isMutable: Boolean get() {
    return when (mutabilityCategory) {
        MutabilityCategory.Immutable -> false
        MutabilityCategory.Declared -> true
        MutabilityCategory.Inherited -> true
        null -> DEFAULT_MUTABILITY
    }
}

/*
val RsExpr.isMutable: Boolean get() {
    return when (this) {
        is RsPathExpr -> {
            val declaration = path.reference.resolve() ?: return DEFAULT_MUTABILITY

            val letExpr = declaration.ancestorStrict<RsLetDecl>()
            if (letExpr != null && letExpr.eq == null) return true

            if (declaration is RsSelfParameter) return declaration.mutability.isMut
            if (declaration is RsPatBinding) return declaration.mutability.isMut
            if (declaration is RsConstant) return declaration.mutability.isMut


            val type = this.type
            if (type is TyReference) return type.mutability.isMut
            if (type is TyUnknown) return DEFAULT_MUTABILITY

            if (declaration is RsEnumVariant) return true
            if (declaration is RsStructItem) return true
            if (declaration is RsFunction) return true

            false
        }
    // is RsFieldExpr -> (expr.type as? TyReference)?.mutable ?: DEFAULT_MUTABILITY // <- this one brings false positives without additional analysis
        is RsUnaryExpr -> mul != null || (expr != null && expr?.isMutable ?: DEFAULT_MUTABILITY)
        else -> DEFAULT_MUTABILITY
    }
}
*/
