package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.RustTypeParameterElement
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.types.util.bounds

data class RustTypeParameterType(val parameter: RustTypeParameterElement) : RustType {

    override fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement> =
        parameter.bounds.mapNotNull { it.bound.traitRef?.trait }

    override fun getNonStaticMethodsIn(project: Project): Sequence<RustFunctionElement> =
        getTraitsImplementedIn(project).flatMap { it.functionList.asSequence() }

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustType = map[this] ?: this

    override fun toString(): String = parameter.name ?: "<unknown>"

}
