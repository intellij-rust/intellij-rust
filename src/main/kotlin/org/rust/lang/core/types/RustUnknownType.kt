package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustTraitItemElement

object RustUnknownType : RustType {

    override fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement> =
        emptySequence()

    override fun getNonStaticMethodsIn(project: Project): Sequence<RustFunctionElement> =
        emptySequence()

    override fun toString(): String = "<unknown>"

}
