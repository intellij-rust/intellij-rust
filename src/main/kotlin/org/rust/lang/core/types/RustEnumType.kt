package org.rust.lang.core.types

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RustEnumItemElement

data class RustEnumType(
    val enum: RustEnumItemElement,
    override val typeArguments: List<RustType> = emptyList()
) : RustStructOrEnumTypeBase {

    override val item = CompletionUtil.getOriginalOrSelf(enum)

    override fun toString(): String = item.name ?: "<anonymous>"

    override fun withTypeArguments(typeArguments: List<RustType>): RustEnumType =
        RustEnumType(item, typeArguments)

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustEnumType =
        RustEnumType(item, typeArguments.map { it.substitute(map) })

}
