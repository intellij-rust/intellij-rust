package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.types.*

object RustTypificationEngine {
    fun typify(named: RsNamedElement): RustType {
        val type = when (named) {
            is RsStructItem -> RustStructType(named)

            is RsEnumItem -> RustEnumType(named)
            is RsEnumVariant -> RustEnumType((named.parent as RsEnumBody).parent as RsEnumItem)

            is RsTypeAlias -> {
                val t = named.typeReference?.type ?: RustUnknownType
                (t as? RustStructOrEnumTypeBase)
                    ?.aliasTypeArguments(named.typeParameters.map(::RustTypeParameterType)) ?: t
            }

            is RsFunction -> deviseFunctionType(named)

            is RsTraitItem -> RustTraitType(named)

            is RsConstant -> named.typeReference?.type ?: RustUnknownType

            is RsSelfParameter -> deviseSelfType(named)

            is RsPatBinding -> inferPatternBindingType(named)

            is RsTypeParameter -> RustTypeParameterType(named)

            else -> RustUnknownType
        }
        return type
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



