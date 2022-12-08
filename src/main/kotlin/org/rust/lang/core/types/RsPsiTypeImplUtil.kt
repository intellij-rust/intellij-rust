/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.withDefaultSubst
import org.rust.lang.core.types.infer.TyLowering
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.recursionGuard


object RsPsiTypeImplUtil {
    fun declaredType(psi: RsStructItem): Ty = TyAdt.valueOf(psi)
    fun declaredType(psi: RsEnumItem): Ty = TyAdt.valueOf(psi)
    fun declaredType(psi: RsTraitItem): Ty = TyTraitObject.valueOf(psi)
    fun declaredType(psi: RsTypeParameter): Ty = TyTypeParameter.named(psi)
    fun declaredType(psi: RsImplItem): Ty = TyTypeParameter.self(psi)
    fun declaredType(psi: RsTypeAlias): Ty {
        val typeReference = psi.typeReference
        if (typeReference != null) return typeReference.typeWithRecursionGuard
        return when (psi.owner) {
            is RsAbstractableOwner.Free -> TyUnknown
            is RsAbstractableOwner.Trait -> TyProjection.valueOf(psi.withDefaultSubst())
            is RsAbstractableOwner.Impl -> TyUnknown
            is RsAbstractableOwner.Foreign -> TyUnknown
        }
    }
}

private val RsTypeReference.typeWithRecursionGuard: Ty
    get() = recursionGuard(this, { TyLowering.lowerTypeReference(this) }) ?: TyUnknown
