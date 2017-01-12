package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem

object RustUnknownType : RustType {

    override fun getTraitsImplementedIn(project: Project): Sequence<RsTraitItem> =
        emptySequence()

    override fun getNonStaticMethodsIn(project: Project): Sequence<RsFunction> =
        emptySequence()

    override fun toString(): String = "<unknown>"

}
