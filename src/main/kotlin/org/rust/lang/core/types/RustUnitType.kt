package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

object RustUnitType : RustTypeBase() {

    override fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement> =
        emptySequence()

    override fun getNonStaticMethodsIn(project: Project): Sequence<RustFunctionElement> =
        emptySequence()

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitUnitType(this)

    override fun toString(): String = "()"
}

