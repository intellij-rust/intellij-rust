package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.types.util.bounds
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustTypeParameterType(val parameter: RustTypeParamElement) : RustTypeBase() {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTypeParameter(this)

    override fun toString(): String = parameter.name ?: "<unknown>"

    override fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement> =
        parameter.bounds.mapNotNull { it.bound.traitRef?.trait }

    override fun getNonStaticMethodsIn(project: Project): Sequence<RustFnElement> =
        getTraitsImplementedIn(project).flatMap { it.traitMethodMemberList.asSequence() }

}
