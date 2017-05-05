package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.findIteratorItemType
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.type
import org.rust.lang.core.types.types.*

fun inferDeclarationType(decl: RsNamedElement): RustType {
    return when (decl) {
        is RsStructItem -> RustStructType(decl)

        is RsEnumItem -> RustEnumType(decl)
        is RsEnumVariant -> RustEnumType(decl.parentEnum)

        is RsTypeAlias -> {
            val t = decl.typeReference?.type ?: RustUnknownType
            (t as? RustStructOrEnumTypeBase)
                ?.aliasTypeArguments(decl.typeParameters.map(::RustTypeParameterType)) ?: t
        }

        is RsFunction -> deviseFunctionType(decl)

        is RsTraitItem -> RustTraitType(decl)

        is RsConstant -> decl.typeReference?.type ?: RustUnknownType

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
                is RsForExpr -> findIteratorItemType(decl.project, parent.expr?.type ?: RustUnknownType)
                else -> null
            } ?: RustUnknownType

            inferPatternBindingType(decl, pattern, patternType)
        }

        is RsTypeParameter -> RustTypeParameterType(decl)

        else -> RustUnknownType
    }
}

fun inferTypeReferenceType(ref: RsTypeReference): RustType {
    return when (ref) {
        is RsTupleType -> {
            val single = ref.typeReferenceList.singleOrNull()
            if (single != null && ref.rparen.getPrevNonCommentSibling()?.elementType != RsElementTypes.COMMA) {
                return inferTypeReferenceType(single)
            }
            if (ref.typeReferenceList.isEmpty()) {
                return RustUnitType
            }
            RustTupleType(ref.typeReferenceList.map(::inferTypeReferenceType))
        }

        is RsBaseType -> {
            val path = ref.path ?: return RustUnknownType
            if (path.path == null && !path.hasColonColon) {
                val primitiveType = RustPrimitiveType.fromTypeName(path.referenceName)
                if (primitiveType != null) return primitiveType
            }
            val target = ref.path?.reference?.resolve() as? RsNamedElement
                ?: return RustUnknownType
            val typeArguments = path.typeArgumentList?.typeReferenceList.orEmpty()
            inferDeclarationType(target)
                .withTypeArguments(typeArguments.map { it.type })

        }

        is RsRefLikeType -> {
            val base = ref.typeReference ?: return RustUnknownType
            val mutable = ref.isMut
            if (ref.isRef) {
                RustReferenceType(inferTypeReferenceType(base), mutable)
            } else {
                if (ref.mul != null) { //Raw pointers
                    RustPointerType(inferTypeReferenceType(base), mutable)
                } else {
                    RustUnknownType
                }
            }
        }

        is RsArrayType -> {
            val componentType = ref.typeReference?.type ?: RustUnknownType
            val size = ref.arraySize
            if (size == null) {
                RustSliceType(componentType)
            } else {
                RustArrayType(componentType, size)
            }
        }

        else -> RustUnknownType
    }
}

/**
 * Devises type for the given (implicit) self-argument
 */
private fun deviseSelfType(self: RsSelfParameter): RustType {
    val impl = self.parentOfType<RsImplItem>()
    var Self: RustType = if (impl != null) {
        impl.typeReference?.type ?: return RustUnknownType
    } else {
        val trait = self.parentOfType<RsTraitItem>()
            ?: return RustUnknownType
        RustTypeParameterType(trait)
    }

    if (self.isRef) {
        Self = RustReferenceType(Self, mutable = self.isMut)
    }

    return Self
}

private fun deviseFunctionType(fn: RsFunction): RustFunctionType {
    val paramTypes = mutableListOf<RustType>()

    val self = fn.selfParameter
    if (self != null) {
        paramTypes += deviseSelfType(self)
    }

    paramTypes += fn.valueParameters.map { it.typeReference?.type ?: RustUnknownType }

    return RustFunctionType(paramTypes, fn.retType?.typeReference?.type ?: RustUnitType)
}

