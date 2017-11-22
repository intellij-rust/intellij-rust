/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
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
            val (target, subst) = path.reference.advancedResolve() ?: return TyUnknown

            if (target is RsTraitOrImpl && type.isCself) {
                TyTypeParameter.self(target)
            } else {
                (target as? RsTypeDeclarationElement ?: return TyUnknown).declaredType.substitute(subst)
            }
        }

        is RsRefLikeType -> {
            val base = type.typeReference
            when {
                type.isRef -> TyReference(inferTypeReferenceType(base), type.mutability)
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

        is RsImplTraitType -> {
            TyAnon(type.polyboundList.mapNotNull { it.bound.traitRef?.resolveToBoundTrait })
        }
        else -> TyUnknown
    }
}
