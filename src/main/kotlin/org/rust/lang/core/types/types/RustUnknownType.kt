package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.types.RustType

object RustUnknownType : RustType {

    override fun getTraitsImplementedIn(project: Project): Collection<RsTraitItem> =
        emptyList()

    override fun getMethodsIn(project: Project): Collection<RsFunction> =
        emptyList()

    override fun canUnifyWith(other: RustType, project: Project): Boolean = false

    override fun toString(): String = "<unknown>"

}
