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
import org.rust.lang.core.psi.ext.RsTypeBearingItemElement
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.types.infer.inferDeclarationType
import org.rust.lang.core.types.infer.inferExpressionType
import org.rust.lang.core.types.infer.inferTypeReferenceType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown


val RsTypeReference.type: Ty
    get() = recursionGuard(this, Computable { inferTypeReferenceType(this) })
        ?: TyUnknown

val RsTypeReference.lifetimeElidable: Boolean get() {
    val typeOwner = topmostType.parent
    return typeOwner !is RsFieldDecl && typeOwner !is RsTupleFieldDecl && typeOwner !is RsTypeAlias
}

val RsTypeReference.topmostType: RsTypeReference
    get() = ancestors
        .drop(1)
        .filterNot { it is RsTypeArgumentList || it is RsPath }
        .takeWhile { it is RsBaseType || it is RsTupleType || it is RsRefLikeType }
        .lastOrNull() as? RsTypeReference ?: this

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
