package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustFnElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.types.unresolved.RustUnresolvedTypeBase

abstract class RustPrimitiveTypeBase : RustUnresolvedTypeBase(), RustType {

    override fun getNonStaticMethodsIn(project: Project): Sequence<RustFnElement> =
        RustImplIndex.findNonStaticMethodsFor(this, project)

    override fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement> =
        emptySequence()
}
