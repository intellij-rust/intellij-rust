package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustTraitItemElement


/**
 * A "trait object" type should not be confused with a trait.
 * Though you use the same path to denote both traits and trait objects,
 * only the latter are types.
 */
data class RustTraitType(val trait: RustTraitItemElement) : RustType {

    override fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement> =
        sequenceOf(trait)

    override fun getNonStaticMethodsIn(project: Project): Sequence<RustFunctionElement> =
        getTraitsImplementedIn(project).flatMap { it.functionList.asSequence() }

    override fun toString(): String = trait.name ?: "<anonymous>"
}
