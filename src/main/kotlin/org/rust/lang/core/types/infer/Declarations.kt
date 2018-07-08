/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.advancedDeepResolve
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type


// Keep in sync with TyFingerprint-create
fun inferTypeReferenceType(ref: RsTypeReference): Ty {
    val type = ref.typeElement
    return when (type) {
        is RsTupleType -> TyTuple(type.typeReferenceList.map(::inferTypeReferenceType))

        is RsBaseType -> {
            if (type.isUnit) return TyUnit
            if (type.isNever) return TyNever
            if (type.isUnderscore) return TyInfer.TyVar()

            val path = type.path ?: return TyUnknown

            val primitiveType = TyPrimitive.fromPath(path)
            if (primitiveType != null) return primitiveType
            val boundElement = path.reference.advancedDeepResolve() ?: return TyUnknown
            val (target, subst) = boundElement

            when {
                target is RsTraitOrImpl && type.isCself -> {
                    if (target is RsImplItem) {
                        target.typeReference?.type ?: TyUnknown
                    } else {
                        TyTypeParameter.self(target)
                    }
                }
                target is RsTraitItem -> TyTraitObject(boundElement.downcast()!!)
                else -> (target as? RsTypeDeclarationElement ?: return TyUnknown).declaredType.substitute(subst)
            }
        }

        is RsRefLikeType -> {
            val base = type.typeReference
            when {
                type.isRef -> TyReference(inferTypeReferenceType(base), type.mutability, type.lifetime.resolve())
                type.isPointer -> TyPointer(inferTypeReferenceType(base), type.mutability)
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
            val paramTypes = type.valueParameterList.valueParameterList
                .map { it.typeReference?.type ?: TyUnknown }
            TyFunction(paramTypes, type.retType?.let { it.typeReference?.type ?: TyUnknown } ?: TyUnit)
        }

        is RsTraitType -> {
            val bounds = type.polyboundList.mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
            if (type.isImpl) {
                TyAnon(type, bounds)
            } else {
                // TODO use all bounds
                bounds.firstOrNull()?.let(::TyTraitObject) ?: TyUnknown
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
