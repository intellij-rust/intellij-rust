package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustFnElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

interface RustType {

    /**
     * Traits explicitly (or implicitly) implemented for this particular type
     */
    fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement>

    /**
     * Non-static methods accessible for this particular type
     */
    fun getNonStaticMethodsIn(project: Project): Sequence<RustFnElement>

    /**
     * Apply positional type arguments to a generic type.
     *
     * This works for `some::path::<Type1, Type2>` case.
     */
    fun withTypeArguments(typeArguments: List<RustType>): RustType = this

    /**
     * Apply named type arguments to a generic type.
     *
     * This works for `struct S<T> { field: T }`, when we
     * know the type of `T` and want to find the type of `field`.
     */
    fun substitute(map: Map<RustTypeParameterType, RustType>): RustType = this

    /**
     * Bindings between formal type parameters and actual type arguments.
     */
    val typeParameterValues: Map<RustTypeParameterType, RustType> get() = emptyMap()

    fun <T> accept(visitor: RustTypeVisitor<T>): T

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String

}
