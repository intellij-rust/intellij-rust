/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type


// Keep in sync with TyFingerprint-create
fun inferTypeReferenceType(ref: RsTypeReference, defaultTraitObjectRegion: Region? = null): Ty {
    return when (val type = ref.typeElement) {
        is RsTupleType -> TyTuple(type.typeReferenceList.map { inferTypeReferenceType(it) })

        is RsBaseType -> when (val kind = type.kind) {
            RsBaseTypeKind.Unit -> TyUnit
            RsBaseTypeKind.Never -> TyNever
            RsBaseTypeKind.Underscore -> TyInfer.TyVar()
            is RsBaseTypeKind.Path -> {
                val path = kind.path
                val primitiveType = TyPrimitive.fromPath(path)
                if (primitiveType != null) return primitiveType
                val boundElement = path.reference.advancedResolve() ?: return TyUnknown
                val (target, subst) = boundElement

                when {
                    target is RsTraitOrImpl && path.hasCself -> {
                        if (target is RsImplItem) {
                            val typeReference = target.typeReference
                            if (typeReference == null || typeReference.isAncestorOf(path)) {
                                // `impl {}` or `impl Self {}`
                                TyUnknown
                            } else {
                                inferTypeReferenceType(typeReference)
                            }
                        } else {
                            TyTypeParameter.self(target)
                        }
                    }
                    target is RsTraitItem -> {
                        TyTraitObject(boundElement.downcast()!!, defaultTraitObjectRegion ?: ReUnknown)
                    }
                    target is RsTypeDeclarationElement -> {
                        val ty = target.declaredType
                            .substituteWithTraitObjectRegion(subst, defaultTraitObjectRegion ?: ReStatic)
                        if (ty is TyAdt && ty.item != target && target is RsTypeAlias) {
                            ty.withAlias(boundElement.downcast()!!)
                        } else {
                            ty
                        }
                    }
                    else -> return TyUnknown
                }
            }
        }

        is RsRefLikeType -> {
            val base = type.typeReference
            when {
                type.isRef -> {
                    val refRegion = type.lifetime.resolve()
                    TyReference(inferTypeReferenceType(base, refRegion), type.mutability, refRegion)
                }
                type.isPointer -> TyPointer(inferTypeReferenceType(base, ReStatic), type.mutability)
                else -> TyUnknown
            }
        }

        is RsArrayType -> {
            val componentType = type.typeReference.type
            if (type.isSlice) {
                TySlice(componentType)
            } else {
                TyArray(componentType, type.arraySize)
            }
        }

        is RsFnPointerType -> {
            val paramTypes = type.valueParameters.map { it.typeReference?.type ?: TyUnknown }
            TyFunction(paramTypes, type.retType?.let { it.typeReference?.type ?: TyUnknown } ?: TyUnit)
        }

        is RsTraitType -> {
            val traitBounds = type.polyboundList.mapNotNull { it.bound.traitRef?.resolveToBoundTrait() }
            val lifetimeBounds = type.polyboundList.mapNotNull { it.bound.lifetime }
            if (type.isImpl) {
                TyAnon(type, traitBounds)
            } else {  // TODO: use all bounds
                TyTraitObject(
                    traitBounds.firstOrNull() ?: return TyUnknown,
                    lifetimeBounds.firstOrNull()?.resolve() ?: defaultTraitObjectRegion ?: ReStatic
                )
            }
        }

        else -> TyUnknown
    }
}

fun RsLifetime?.resolve(): Region {
    this ?: return ReUnknown
    if (referenceName == "'static") return ReStatic
    val resolved = reference.resolve()
    return if (resolved is RsLifetimeParameter) ReEarlyBound(resolved) else ReUnknown
}

private fun <T> TypeFoldable<T>.substituteWithTraitObjectRegion(
    subst: Substitution,
    defaultTraitObjectRegion: Region
): T = foldWith(object : TypeFolder {
    override fun foldTy(ty: Ty): Ty = when {
        ty is TyTypeParameter -> handleTraitObject(ty) ?: ty
        ty.needToSubstitute -> ty.superFoldWith(this)
        else -> ty
    }

    override fun foldRegion(region: Region): Region =
        (region as? ReEarlyBound)?.let { subst[it] } ?: region

    fun handleTraitObject(paramTy: TyTypeParameter): Ty? {
        val ty = subst[paramTy]
        if (ty !is TyTraitObject || ty.region !is ReUnknown) return ty
        val bounds = paramTy.regionBounds
        val region = when (bounds.size) {
            0 -> defaultTraitObjectRegion
            1 -> bounds.single().substitute(subst)
            else -> ReUnknown
        }
        return TyTraitObject(ty.trait, region)
    }
})
