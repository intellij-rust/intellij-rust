package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.types.unresolved.RustUnresolvedTypeBase
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

object RustUnknownType : RustUnresolvedTypeBase(), RustType {

    override fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement> =
        emptySequence()

    override fun getNonStaticMethodsIn(project: Project): Sequence<RustFunctionElement> =
        emptySequence()

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitUnknown(this)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitUnknown(this)

    override fun toString(): String = "<unknown>"

}
