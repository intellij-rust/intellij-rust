package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustFnElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

interface RustType {

    fun <T> accept(visitor: RustTypeVisitor<T>): T

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String

    /**
     * Traits explicitly (or implicitly) implemented for this particular type
     */
    fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement>

    /**
     * Non-static methods accessible for this particular type
     */
    fun getNonStaticMethodsIn(project: Project): Sequence<RustFnElement>

}
