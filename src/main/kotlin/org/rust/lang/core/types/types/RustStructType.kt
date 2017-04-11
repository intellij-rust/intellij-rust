package org.rust.lang.core.types.types

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.RustType

class RustStructType(
    struct: RsStructItem,
    override val typeArgumentsMapping: List<RustTypeParameterType> = struct.typeParameters.map(::RustTypeParameterType),
    override val typeArguments: List<RustType> = typeArgumentsMapping
) : RustStructOrEnumTypeBase {

    override val item = CompletionUtil.getOriginalOrSelf(struct)

    override fun toString(): String = item.name ?: "<anonymous>"

    override fun withTypeArguments(typeArguments: List<RustType>): RustStructType =
        super.withTypeArguments(typeArguments) as RustStructType

    override fun aliasTypeArguments(typeArguments: List<RustTypeParameterType>): RustStructType =
        RustStructType(item, typeArguments, this.typeArguments)

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustStructType =
        RustStructType(item, typeArgumentsMapping, typeArguments.map { it.substitute(map) })

    override fun equals(other: Any?): Boolean =
        other is RustStructType && item == other.item && typeArguments == other.typeArguments

    override fun hashCode(): Int =
        item.hashCode() xor typeArguments.hashCode()
}
