package org.rust.lang.core.types

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

interface RustType {

    fun <T> accept(visitor: RustTypeVisitor<T>): T

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String

    /**
     * Inherent and non inherent impl.
     *
     * TODO: separate two kinds of impls and filter by visible traits
     */
    val impls: Sequence<RustImplItemElement> get() = emptySequence()

    val allMethods: Sequence<RustImplMethodMemberElement>
        get() = impls.flatMap { it.implBody?.implMethodMemberList.orEmpty().asSequence() }

    val nonStaticMethods: Sequence<RustImplMethodMemberElement>
        get() = allMethods.filter { !it.isStatic }

    val staticMethods: Sequence<RustImplMethodMemberElement>
        get() = allMethods.filter { it.isStatic }

    /**
     * Strips all the references and returns the name of the resulting nominal type,
     * if it is indeed nominal.
     *
     * See `RustUnresolvedType#nominalTypeName`
     */
    val baseTypeName: String? get() = null
}
