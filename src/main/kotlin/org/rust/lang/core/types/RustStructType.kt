package org.rust.lang.core.types

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustStructType(
    struct: RustStructItemElement,
    typeArguments: List<RustType> = emptyList()
) : RustStructOrEnumTypeBase(typeArguments) {

    override val item = CompletionUtil.getOriginalOrSelf(struct)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitStruct(this)

    override fun toString(): String = item.name ?: "<anonymous>"

    override fun withTypeArguments(typeArguments: List<RustType>): RustStructType =
        RustStructType(item, typeArguments)

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustStructType =
        RustStructType(item, typeArguments.map { it.substitute(map) })
}
