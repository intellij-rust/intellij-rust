/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import com.intellij.openapi.util.Computable
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.ide.utils.recursionGuard
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.ty.TyUnknown


val RsTypeReference.type: Ty
    get() = recursionGuard(this, Computable { inferTypeReferenceType(this) })
        ?: TyUnknown

val RsTypeElement.lifetimeElidable: Boolean get() {
    val typeOwner = owner.parent
    return typeOwner !is RsFieldDecl && typeOwner !is RsTupleFieldDecl && typeOwner !is RsTypeAlias
}

val RsTypeBearingItemElement.type: Ty
    get() = CachedValuesManager.getCachedValue(this, CachedValueProvider {
        val type = recursionGuard(this, Computable { inferDeclarationType(this) })
            ?: TyUnknown
        CachedValueProvider.Result.create(type, PsiModificationTracker.MODIFICATION_COUNT)
    })

val RsFunction.inferenceContext: RsInferenceContext
    get() = CachedValuesManager.getCachedValue(this, CachedValueProvider {
        CachedValueProvider.Result.create(inferTypesIn(this), PsiModificationTracker.MODIFICATION_COUNT)
    })

val RsPatBinding.type: Ty
    get() = parentOfType<RsFunction>()?.inferenceContext?.getBindingType(this) ?: TyUnknown

val RsExpr.type: Ty
    get() = parentOfType<RsFunction>()?.inferenceContext?.getExprType(this) ?: inferOutOfFnExpressionType(this)

val RsExpr.declaration: RsCompositeElement?
    get() = when (this) {
        is RsPathExpr -> path.reference.resolve()
        else -> null
    }

private val DEFAULT_MUTABILITY = true

val RsExpr.isMutable: Boolean get() {
    return when (this) {
        is RsPathExpr -> {
            val declaration = path.reference.resolve() ?: return DEFAULT_MUTABILITY
            if (declaration is RsSelfParameter) return declaration.isMut
            if (declaration is RsPatBinding && declaration.isMut) return true
            if (declaration is RsConstant) return declaration.isMut

            val type = this.type
            if (type is TyReference) return type.mutable

            val letExpr = declaration.parentOfType<RsLetDecl>()
            if (letExpr != null && letExpr.eq == null) return true
            if (type is TyUnknown) return DEFAULT_MUTABILITY
            if (declaration is RsEnumVariant) return true

            false
        }
    // is RsFieldExpr -> (expr.type as? TyReference)?.mutable ?: DEFAULT_MUTABILITY // <- this one brings false positives without additional analysis
        is RsUnaryExpr -> mul != null || (expr != null && expr?.isMutable ?: DEFAULT_MUTABILITY)
        else -> DEFAULT_MUTABILITY
    }
}
