/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.types.ty.*


object RsPsiTypeImplUtil {
    fun declaredType(psi: RsStructItem): Ty = TyStruct.valueOf(psi)
    fun declaredType(psi: RsEnumItem): Ty = TyEnum.valueOf(psi)
    fun declaredType(psi: RsTraitItem): Ty = TyTraitObject.valueOf(psi)
    fun declaredType(psi: RsTypeParameter): Ty = TyTypeParameter.named(psi)
    fun declaredType(psi: RsTypeAlias): Ty {
        val typeReference = psi.typeReference
        if (typeReference != null) return typeReference.type
        return when (psi.owner) {
            is RsAbstractableOwner.Free -> TyUnknown
            is RsAbstractableOwner.Trait -> TyTypeParameter.associated(psi)
            is RsAbstractableOwner.Impl -> TyUnknown
            is RsAbstractableOwner.Foreign -> TyUnknown
        }
    }
    fun declaredType(psi: RsImplItem): Ty = TyTypeParameter.self(psi)
}
