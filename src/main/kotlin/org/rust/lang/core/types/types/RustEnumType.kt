package org.rust.lang.core.types.types

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.RustType

class RustEnumType(
    enum: RsEnumItem,
    override val typeArgumentsMapping: List<RustTypeParameterType> = enum.typeParameters.map(::RustTypeParameterType),
    override val typeArguments: List<RustType> = typeArgumentsMapping
) : RustStructOrEnumTypeBase {

    override val item = CompletionUtil.getOriginalOrSelf(enum)

    override fun toString(): String = item.name ?: "<anonymous>"

    override fun withTypeArguments(typeArguments: List<RustType>): RustEnumType =
        super.withTypeArguments(typeArguments) as RustEnumType

    override fun aliasTypeArguments(typeArguments: List<RustTypeParameterType>): RustEnumType =
        RustEnumType(item, typeArguments, this.typeArguments)

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustEnumType =
        RustEnumType(item, typeArgumentsMapping, typeArguments.map { it.substitute(map) })

    override fun equals(other: Any?): Boolean =
        other is RustEnumType && item == other.item && typeArguments == other.typeArguments

    override fun hashCode(): Int =
        item.hashCode() xor typeArguments.hashCode()

}
