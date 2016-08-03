package org.rust.lang.core.types

import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor


/**
 * A "trait object" type should not be confused with a trait.
 * Though you use the same path to denote both traits and trait objects,
 * only the latter are types.
 */
class RustTraitObjectType(val trait: RustTraitItemElement) : RustType {
    override val traits: Sequence<RustTraitItemElement> get() = sequenceOf(trait)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTraitObject(this)

    override fun equals(other: Any?): Boolean = other is RustTraitObjectType && trait == other.trait

    override fun hashCode(): Int = trait.hashCode() * 677 + 10061

    override fun toString(): String = trait.name ?: "<anonymous>"
}
