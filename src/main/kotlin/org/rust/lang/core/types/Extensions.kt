/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.infer.RsInferenceResult
import org.rust.lang.core.types.infer.inferOutOfFnExpressionType
import org.rust.lang.core.types.infer.inferTypeReferenceType
import org.rust.lang.core.types.infer.inferTypesIn
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.openapiext.recursionGuard


val RsTypeReference.type: Ty
    get() = recursionGuard(this, Computable { inferTypeReferenceType(this) })
        ?: TyUnknown

val RsTypeElement.lifetimeElidable: Boolean get() {
    val typeOwner = owner.parent
    return typeOwner !is RsFieldDecl && typeOwner !is RsTupleFieldDecl && typeOwner !is RsTypeAlias
}

val RsFunction.inference: RsInferenceResult
    get() = CachedValuesManager.getCachedValue(this, {
        CachedValueProvider.Result.create(inferTypesIn(this), PsiModificationTracker.MODIFICATION_COUNT)
    })

val PsiElement.inference: RsInferenceResult?
    get() = (ancestorStrict<RsItemElement>() as? RsFunction)?.inference

val RsPatBinding.type: Ty
    get() = inference?.getBindingType(this) ?: TyUnknown

val RsExpr.type: Ty
    get() = inference?.getExprType(this) ?: inferOutOfFnExpressionType(this)

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

private val DEFAULT_MUTABILITY = true

val RsExpr.isMutable: Boolean get() {
    return when (this) {
        is RsPathExpr -> {
            val declaration = path.reference.resolve() ?: return DEFAULT_MUTABILITY
            if (declaration is RsSelfParameter) return declaration.mutability.isMut
            if (declaration is RsPatBinding && declaration.mutability.isMut) return true
            if (declaration is RsConstant) return declaration.mutability.isMut

            val type = this.type
            if (type is TyReference) return type.mutability.isMut

            val letExpr = declaration.ancestorStrict<RsLetDecl>()
            if (letExpr != null && letExpr.eq == null) return true
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
