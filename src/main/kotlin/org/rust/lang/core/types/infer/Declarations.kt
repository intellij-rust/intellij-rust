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
        is RsTypeParameter -> TyTypeParameter(decl)
        is RsPatBinding -> throw IllegalArgumentException()
        else -> TyUnknown
    }
}

private val RsCallExpr.declaration: RsFunction?
    get() = (expr as? RsPathExpr)?.path?.reference?.resolve() as? RsFunction

private val RsMethodCallExpr.declaration: RsFunction?
    get() = reference.resolve() as? RsFunction

fun inferTypeReferenceType(ref: RsTypeReference): Ty {
    val type = ref.typeElement
    return when (type) {
        is RsTupleType -> TyTuple(type.typeReferenceList.map(::inferTypeReferenceType))

        is RsBaseType -> {
            if (type.isUnit) return TyUnit

            val path = type.path ?: return TyUnknown

            val primitiveType = TyPrimitive.fromPath(path)
            if (primitiveType != null) return primitiveType
            val (target, subst) = path.reference.advancedResolve() ?: return TyUnknown

            if (target is RsTraitOrImpl && type.isCself) {
                TyTypeParameter(target)
            } else {
                inferDeclarationType(target as? RsNamedElement ?: return TyUnknown).substitute(subst)
            }
        }

        is RsRefLikeType -> {
            val base = type.typeReference
            val mutable = type.isMut
            if (type.isRef) {
                TyReference(inferTypeReferenceType(base), mutable)
            } else {
                if (type.isPointer) { //Raw pointers
                    TyPointer(inferTypeReferenceType(base), mutable)
                } else {
                    TyUnknown
                }
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

        else -> TyUnknown
    }
}

private fun deviseAliasType(decl: RsTypeAlias): Ty {
    val typeReference = decl.typeReference
    if (typeReference != null) return typeReference.type

    val trait = decl.parentOfType<RsTraitItem>()
        ?: return TyUnknown
    val name = decl.name ?: return TyUnknown
    return TyTypeParameter(trait, name)
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
        TyTypeParameter(trait)
    }

    if (self.isRef) {
        Self = TyReference(Self, mutable = self.isMut)
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

    return TyFunction(paramTypes, fn.returnType)
}

