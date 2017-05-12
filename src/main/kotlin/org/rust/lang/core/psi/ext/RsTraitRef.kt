package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTraitRef
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type

val RsTraitRef.resolveToTrait: RsTraitItem?
    get() = path.reference.resolve() as? RsTraitItem

val RsTraitRef.resolveToBoundTrait: BoundElement<RsTraitItem>? get() {
    val trait = resolveToTrait ?: return null
    trait.typeParameters.map {it.typeReference}
    val typeParameters = trait.typeParameters.map { TyTypeParameter(it) }
    val typeArguments = path.typeArgumentList?.typeReferenceList.orEmpty().map { it.type }
    val args = typeParameters.zip(typeArguments)
        .mapNotNull { (param, arg) ->
            if (param is TyTypeParameter) param to arg else null
        }
        .toMap()
    return BoundElement(trait, args)
}

