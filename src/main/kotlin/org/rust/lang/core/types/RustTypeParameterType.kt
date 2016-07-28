package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustTypeParameterType(val parameter: RustTypeParamElement) : RustType {

    override val traits: Sequence<RustTraitItemElement>
        get() = parameter.bounds.mapNotNull { it.bound.traitRef?.path?.reference?.resolve() as? RustTraitItemElement }

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTypeParameter(this)

    override fun toString(): String = parameter.name ?: "<unknown>"

    override fun equals(other: Any?): Boolean = other is RustTypeParameterType && other.parameter === parameter

    override fun hashCode(): Int = parameter.hashCode()
}

private val RustTypeParamElement.bounds: Sequence<RustPolyboundElement> get() {
    val owner = parent?.parent as? RustGenericDeclaration
    val whereBounds = owner?.whereClause?.wherePredList.orEmpty().asSequence()
        .filter { (it.type as? RustPathTypeElement)?.path?.reference?.resolve() == this }
        .flatMap { it.typeParamBounds?.polyboundList.orEmpty().asSequence() }
    return typeParamBounds?.polyboundList.orEmpty().asSequence() + whereBounds
}

