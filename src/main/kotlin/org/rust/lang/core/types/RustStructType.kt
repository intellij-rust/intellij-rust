package org.rust.lang.core.types

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RustStructItemElement

data class RustStructType(
    val struct: RustStructItemElement,
    override val typeArguments: List<RustType> = emptyList()
) : RustStructOrEnumTypeBase {

    override val item = CompletionUtil.getOriginalOrSelf(struct)

    override fun toString(): String = item.name ?: "<anonymous>"

    override fun withTypeArguments(typeArguments: List<RustType>): RustStructType =
        RustStructType(item, typeArguments)

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustStructType =
        RustStructType(item, typeArguments.map { it.substitute(map) })
}
