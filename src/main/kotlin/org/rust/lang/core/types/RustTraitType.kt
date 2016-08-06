package org.rust.lang.core.types

import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor


/**
 * A "trait object" type should not be confused with a trait.
 * Though you use the same path to denote both traits and trait objects,
 * only the latter are types.
 */
class RustTraitType(val trait: RustTraitItemElement) : RustTypeBase() {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTrait(this)

    override fun toString(): String = trait.name ?: "<anonymous>"
}
