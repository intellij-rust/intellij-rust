package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.findIteratorItemType
import org.rust.lang.core.types.Ty
import org.rust.lang.core.types.type
import org.rust.lang.core.types.types.*

fun inferDeclarationType(decl: RsNamedElement): Ty {
    return when (decl) {
        is RsStructItem -> RustStructType(decl)

        is RsEnumItem -> RustEnumType(decl)
        is RsEnumVariant -> RustEnumType(decl.parentEnum)

        is RsTypeAlias -> {
            val t = decl.typeReference?.type ?: TyUnknown
            (t as? RustStructOrEnumTypeBase)
                ?.aliasTypeArguments(decl.typeParameters.map(::RustTypeParameterType)) ?: t
        }

        is RsFunction -> deviseFunctionType(decl)

        is RsTraitItem -> RustTraitType(decl)

        is RsConstant -> decl.typeReference?.type ?: TyUnknown

        is RsSelfParameter -> deviseSelfType(decl)

        is RsPatBinding -> {
            val pattern = decl.topLevelPattern
            val parent = pattern.parent
            val patternType = when (parent) {
                is RsLetDecl ->
                    // use type ascription, if present or fallback to the type of the initializer expression
                    parent.typeReference?.type ?: parent.expr?.type

                is RsValueParameter -> parent.typeReference?.type
                is RsCondition -> parent.expr.type
                is RsMatchArm -> parent.parentOfType<RsMatchExpr>()?.expr?.type
                is RsForExpr -> findIteratorItemType(decl.project, parent.expr?.type ?: TyUnknown)
                else -> null
            } ?: TyUnknown

            inferPatternBindingType(decl, pattern, patternType)
        }

        is RsTypeParameter -> RustTypeParameterType(decl)

        else -> TyUnknown
    }
}

fun inferTypeReferenceType(ref: RsTypeReference): Ty {
    return when (ref) {
        is RsTupleType -> {
            val single = ref.typeReferenceList.singleOrNull()
            if (single != null && ref.rparen.getPrevNonCommentSibling()?.elementType != RsElementTypes.COMMA) {
                return inferTypeReferenceType(single)
            }
            if (ref.typeReferenceList.isEmpty()) {
                return TyUnit
            }
            RustTupleType(ref.typeReferenceList.map(::inferTypeReferenceType))
        }

        is RsBaseType -> {
            val path = ref.path ?: return TyUnknown
            if (path.path == null && !path.hasColonColon) {
                val primitiveType = TyPrimitive.fromTypeName(path.referenceName)
                if (primitiveType != null) return primitiveType
            }
            val target = ref.path?.reference?.resolve() as? RsNamedElement
                ?: return TyUnknown
            val typeArguments = path.typeArgumentList?.typeReferenceList.orEmpty()
            inferDeclarationType(target)
                .withTypeArguments(typeArguments.map { it.type })

        }

        is RsRefLikeType -> {
            val base = ref.typeReference ?: return TyUnknown
            val mutable = ref.isMut
            if (ref.isRef) {
                RustReferenceType(inferTypeReferenceType(base), mutable)
            } else {
                if (ref.mul != null) { //Raw pointers
                    RustPointerType(inferTypeReferenceType(base), mutable)
                } else {
                    TyUnknown
                }
            }
        }

        is RsArrayType -> {
            val componentType = ref.typeReference?.type ?: TyUnknown
            val size = ref.arraySize
            if (size == null) {
                RustSliceType(componentType)
            } else {
                RustArrayType(componentType, size)
            }
        }

        else -> TyUnknown
    }
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
        RustTypeParameterType(trait)
    }

    if (self.isRef) {
        Self = RustReferenceType(Self, mutable = self.isMut)
    }

    return Self
}

private fun deviseFunctionType(fn: RsFunction): RustFunctionType {
    val paramTypes = mutableListOf<Ty>()

    val self = fn.selfParameter
    if (self != null) {
        paramTypes += deviseSelfType(self)
    }

    paramTypes += fn.valueParameters.map { it.typeReference?.type ?: TyUnknown }

    return RustFunctionType(paramTypes, fn.retType?.typeReference?.type ?: TyUnit)
}

