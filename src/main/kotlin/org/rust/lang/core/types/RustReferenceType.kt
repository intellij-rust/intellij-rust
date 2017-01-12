package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFunction

data class RustReferenceType(val referenced: RustType, val mutable: Boolean = false) : RustType {

    override fun getNonStaticMethodsIn(project: Project): Sequence<RsFunction> =
        super.getNonStaticMethodsIn(project) + stripAllRefsIfAny().getNonStaticMethodsIn(project)

    override fun toString(): String = "${if (mutable) "&mut" else "&"} $referenced"
}
