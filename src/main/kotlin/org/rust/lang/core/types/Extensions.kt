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
import org.rust.lang.core.types.infer.mutabilityCategory
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.recursionGuard


val RsTypeReference.type: Ty
    get() = recursionGuard(this, Computable { inferTypeReferenceType(this) })
        ?: TyUnknown

val RsTypeElement.lifetimeElidable: Boolean
    get() {
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

val RsTraitOrImpl.selfType: Ty
    get() = when (this) {
        is RsImplItem -> typeReference?.type ?: TyUnknown
        is RsTraitItem -> TyTypeParameter.self(this)
        else -> error("Unreachable")
    }

fun Ty.builtinDeref(explicit: Boolean = true): Pair<Ty, Mutability>? =
    when {
        this is TyReference -> Pair(referenced, mutability)
        this is TyPointer && explicit -> Pair(referenced, mutability)
        else -> null
    }

val RsExpr.isDereference: Boolean get() = this is RsUnaryExpr && this.mul != null


val RsExpr.isMutable: Boolean get() = mutabilityCategory?.isMutable ?: Mutability.DEFAULT_MUTABILITY.isMut
