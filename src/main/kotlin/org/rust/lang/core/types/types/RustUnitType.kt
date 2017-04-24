package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.types.RustType

object RustUnitType : RustType {

    override fun canUnifyWith(other: RustType, project: Project): Boolean =
        other is RustUnitType

    override fun getTraitsImplementedIn(project: Project): Sequence<RsTraitItem> =
        emptySequence()

    override fun getMethodsIn(project: Project): Sequence<RsFunction> =
        emptySequence()

    override fun toString(): String = "()"
}

