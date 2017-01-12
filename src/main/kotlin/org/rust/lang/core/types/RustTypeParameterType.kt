package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.types.util.bounds

data class RustTypeParameterType(val parameter: RsTypeParameter) : RustType {

    override fun getTraitsImplementedIn(project: Project): Sequence<RsTraitItem> =
        parameter.bounds.mapNotNull { it.bound.traitRef?.trait }

    override fun getNonStaticMethodsIn(project: Project): Sequence<RsFunction> =
        getTraitsImplementedIn(project).flatMap { it.functionList.asSequence() }

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustType = map[this] ?: this

    override fun toString(): String = parameter.name ?: "<unknown>"

}
