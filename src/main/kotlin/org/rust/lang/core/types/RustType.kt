package org.rust.lang.core.types

import org.rust.lang.core.psi.RustFnElement
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustTraitItemElement
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

    /**
     * Traits implemented by this type, for which there are now impls (e.g., derived traits or generic bounds)
     */
    val traits: Sequence<RustTraitItemElement> get() = emptySequence()

    val allMethods: Sequence<RustFnElement>
        get() = impls.flatMap { it.implBody?.implMethodMemberList.orEmpty().asSequence() } +
            traits.flatMap { it.traitBody.traitMethodMemberList.orEmpty().asSequence()  }

    val nonStaticMethods: Sequence<RustFnElement>
        get() = allMethods.filter { !it.isStatic }

    val staticMethods: Sequence<RustFnElement>
        get() = allMethods.filter { it.isStatic }

    /**
     * Strips all the references and returns the name of the resulting nominal type,
     * if it is indeed nominal.
     *
     * See `RustUnresolvedType#nominalTypeName`
     */
    val baseTypeName: String? get() = null
}
