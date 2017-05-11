package org.rust.lang.core.types

import com.intellij.openapi.util.Computable
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.ide.utils.recursionGuard
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.ext.RsTypeBearingItemElement
import org.rust.lang.core.types.infer.inferDeclarationType
import org.rust.lang.core.types.infer.inferExpressionType
import org.rust.lang.core.types.infer.inferTypeReferenceType
import org.rust.lang.core.types.types.TyUnknown


val RsTypeReference.type: Ty
    get() = recursionGuard(this, Computable { inferTypeReferenceType(this) })
        ?: TyUnknown

val RsTypeBearingItemElement.type: Ty
    get() = CachedValuesManager.getCachedValue(this, CachedValueProvider {
        val type = recursionGuard(this, Computable { inferDeclarationType(this) })
            ?: TyUnknown
        CachedValueProvider.Result.create(type, PsiModificationTracker.MODIFICATION_COUNT)
    })

val RsExpr.type: Ty
    get() = CachedValuesManager.getCachedValue(this, CachedValueProvider {
        val type = recursionGuard(this, Computable { inferExpressionType(this) })
            ?: TyUnknown
        CachedValueProvider.Result.create(type, PsiModificationTracker.MODIFICATION_COUNT)
    })
