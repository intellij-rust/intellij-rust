package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustFnElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.types.visitors.impl.RustEqualityTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustHashCodeComputingTypeVisitor

abstract class RustTypeBase : RustType {

    final override fun equals(other: Any?): Boolean = other is RustType && accept(RustEqualityTypeVisitor(other))

    final override fun hashCode(): Int = accept(RustHashCodeComputingTypeVisitor())

    override fun getNonStaticMethodsIn(project: Project): Sequence<RustFnElement> =
        RustImplIndex.findNonStaticMethodsFor(this, project)

    override fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement> =
        RustImplIndex.findImplsFor(this, project).mapNotNull { it.traitRef?.trait }
}
