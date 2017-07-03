/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTraitRef
import org.rust.lang.core.resolve.fnOutputParam
import org.rust.lang.core.resolve.fnTypeArgsParam
import org.rust.lang.core.resolve.isAnyFnTrait
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

val RsTraitRef.resolveToTrait: RsTraitItem?
    get() = path.reference.resolve() as? RsTraitItem

val RsTraitRef.resolveToBoundTrait: BoundElement<RsTraitItem>? get() {
    val trait = resolveToTrait ?: return null
    val typeArguments = if (trait.isAnyFnTrait) {
        val argsParam = trait.fnTypeArgsParam
        val outputParam = trait.fnOutputParam
        val args = path.valueParameterList?.valueParameterList
            ?.map { it.typeReference?.type ?: TyUnknown }
        val output = path.retType?.typeReference?.type
        if (argsParam == null || args == null || outputParam == null) {
            emptyMap()
        } else {
            mapOf(argsParam to TyTuple(args), outputParam to (output ?: TyUnit))
        }
    } else {
        val params = trait.typeParameters.map { TyTypeParameter(it) }
        val args = path.typeArgumentList?.typeReferenceList.orEmpty().map { it.type }
        params.zip(args).toMap()
    }

    return BoundElement(trait, typeArguments)
}

