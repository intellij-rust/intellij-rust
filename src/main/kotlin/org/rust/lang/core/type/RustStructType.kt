package org.rust.lang.core.type

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.impl.mixin.isStatic
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.type.visitors.RustTypeVisitor

class RustStructType(val struct: RustStructItemElement) : RustType {

    /**
     * Impls without traits, like `impl S { ... }`
     *
     * You don't need to import such impl to be able to use its methods.
     * There may be several `impl` blocks for the same type and they may
     * be spread across different files and modules (we don't handle this yet)
     */
    val inheritedImpls: Collection<RustImplItemElement> by lazy {
        struct.parentOfType<RustMod>()
            ?.itemList.orEmpty()
                .filterIsInstance<RustImplItemElement>()
                .filter { it.traitRef == null && (it.type?.resolvedType == this) }
    }

    val allMethods: Collection<RustImplMethodMemberElement>
        get() = inheritedImpls.flatMap { it.implBody?.implMethodMemberList.orEmpty() }

    val nonStaticMethods: Collection<RustImplMethodMemberElement>
        get() = allMethods.filter { !it.isStatic }

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitStruct(this)

    override fun toString(): String = "<struct type $struct>"

    override fun equals(other: Any?): Boolean = other is RustStructType && other.struct === struct

    override fun hashCode(): Int = struct.hashCode() * 10067 + 9631
}
