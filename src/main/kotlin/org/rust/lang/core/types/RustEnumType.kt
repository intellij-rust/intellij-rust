package org.rust.lang.core.types

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RustEnumItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustEnumType(
    enum: RustEnumItemElement,
    typeArguments: List<RustType> = emptyList()
) : RustStructOrEnumTypeBase(typeArguments) {

    override val item = CompletionUtil.getOriginalOrSelf(enum)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitEnum(this)

    override fun toString(): String = item.name ?: "<anonymous>"

    override fun withTypeArguments(typeArguments: List<RustType>): RustEnumType =
        RustEnumType(item, typeArguments)

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustEnumType =
        RustEnumType(item, typeArguments.map { it.substitute(map) })

}
