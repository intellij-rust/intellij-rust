/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type


fun inferTypeReferenceType(ref: RsTypeReference): Ty {
    val type = ref.typeElement
    return when (type) {
        is RsTupleType -> TyTuple(type.typeReferenceList.map(::inferTypeReferenceType))

        is RsBaseType -> {
            if (type.isUnit) return TyUnit
            if (type.isNever) return TyNever

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
            val size = type.arraySize
            if (size == null) {
                TySlice(componentType)
            } else {
                TyArray(componentType, size)
            }
        }

        is RsFnPointerType -> {
            val paramTypes = type.valueParameterList.valueParameterList
                .map { it.typeReference?.type ?: TyUnknown }
            TyFunction(paramTypes, type.retType?.let { it.typeReference?.type ?: TyUnknown } ?: TyUnit)
        }

        else -> TyUnknown
    }
}
