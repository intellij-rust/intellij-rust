package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor


/**
 * A "trait object" type should not be confused with a trait.
 * Though you use the same path to denote both traits and trait objects,
 * only the latter are types.
 */
class RustTraitType(val trait: RustTraitItemElement) : RustTypeBase() {

    override fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement> =
        sequenceOf(trait)

    override fun getNonStaticMethodsIn(project: Project): Sequence<RustFunctionElement> =
        getTraitsImplementedIn(project).flatMap { it.functionList.asSequence() }

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTrait(this)

    override fun toString(): String = trait.name ?: "<anonymous>"
}
