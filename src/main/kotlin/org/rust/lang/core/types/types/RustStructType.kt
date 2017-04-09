package org.rust.lang.core.types.types

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.types.RustType

class RustStructType(
    struct: RsStructItem,
    override val typeArguments: List<RustType> = emptyList()
) : RustStructOrEnumTypeBase {

    override val item = CompletionUtil.getOriginalOrSelf(struct)

    override fun toString(): String = item.name ?: "<anonymous>"

    override fun withTypeArguments(typeArguments: List<RustType>): RustStructType =
        RustStructType(item, typeArguments)

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustStructType =
        RustStructType(item, typeArguments.map { it.substitute(map) })

    override fun equals(other: Any?): Boolean =
        other is RustStructType && item == other.item && typeArguments == other.typeArguments

    override fun hashCode(): Int =
        item.hashCode() xor typeArguments.hashCode()
}
