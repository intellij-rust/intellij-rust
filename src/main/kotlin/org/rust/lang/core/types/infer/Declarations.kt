/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

fun inferDeclarationType(decl: RsNamedElement): Ty {
    return when (decl) {
        is RsStructItem -> TyStruct.valueOf(decl)
        is RsEnumItem -> TyEnum.valueOf(decl)
        is RsEnumVariant -> TyEnum.valueOf(decl.parentEnum)
        is RsTypeAlias -> deviseAliasType(decl)
        is RsFunction -> deviseFunctionType(decl)
        is RsTraitItem -> TyTraitObject(decl)
        is RsConstant -> decl.typeReference?.type ?: TyUnknown
        is RsSelfParameter -> deviseSelfType(decl)
        is RsTypeParameter -> TyTypeParameter.named(decl)
        is RsPatBinding -> throw IllegalArgumentException()
        else -> TyUnknown
    }
}

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
                inferDeclarationType(target as? RsNamedElement ?: return TyUnknown).substitute(subst)
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
            val paramTypes = type.valueParameterList.valueParameterList.orEmpty()
                .map { it.typeReference?.type ?: TyUnknown }
            TyFunction(paramTypes, type.retType?.let { it.typeReference?.type ?: TyUnknown } ?: TyUnit)
        }

        else -> TyUnknown
    }
}

private fun deviseAliasType(decl: RsTypeAlias): Ty {
    val typeReference = decl.typeReference
    if (typeReference != null) return typeReference.type

    if (decl.parentOfType<RsTraitItem>() == null) return TyUnknown
    return TyTypeParameter.associated(decl)
}

/**
 * Devises type for the given (implicit) self-argument
 */
private fun deviseSelfType(self: RsSelfParameter): Ty {
    val impl = self.parentOfType<RsImplItem>()
    var Self: Ty = if (impl != null) {
        impl.typeReference?.type ?: return TyUnknown
    } else {
        val trait = self.parentOfType<RsTraitItem>()
            ?: return TyUnknown
        TyTypeParameter.self(trait)
    }

    if (self.isRef) {
        Self = TyReference(Self, self.mutability)
    }

    return Self
}

private fun deviseFunctionType(fn: RsFunction): TyFunction {
    val paramTypes = mutableListOf<Ty>()

    val self = fn.selfParameter
    if (self != null) {
        paramTypes += deviseSelfType(self)
    }

    paramTypes += fn.valueParameters.map { it.typeReference?.type ?: TyUnknown }

    val ownerType = (fn.owner as? RsFunctionOwner.Impl)?.impl?.typeReference?.type
    val subst = if (ownerType != null) mapOf(TyTypeParameter.self() to ownerType) else emptyMap()

    return TyFunction(paramTypes, fn.returnType).substitute(subst)
}

