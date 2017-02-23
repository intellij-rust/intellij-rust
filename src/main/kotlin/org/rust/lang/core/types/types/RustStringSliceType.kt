package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.resolve.indexes.RsImplIndex

object RustStringSliceType : RustPrimitiveType {

    override fun toString(): String = "str"

    override fun getTraitsImplementedIn(project: Project): Sequence<RsTraitItem> {
        return RsImplIndex.findImplsFor(this, project).mapNotNull { it.traitRef?.trait }
    }

    override fun getMethodsIn(project: Project): Sequence<RsFunction> {
        return RsImplIndex.findMethodsFor(this, project)
    }
}
