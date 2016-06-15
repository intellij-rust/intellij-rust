package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustStructType(val struct: RustStructItemElement) : RustType {

    /**
     * Impls without traits, like `impl S { ... }`
     *
     * You don't need to import such impl to be able to use its methods.
     * There may be several `impl` blocks for the same type and they may
     * be spread across different files and modules (we don't handle this yet)
     */
    val inherentImpls: Collection<RustImplItemElement> by lazy {
        struct.containingMod
            ?.impls.orEmpty()
                .filter { it.traitRef == null && (it.type?.resolvedType == this) }
    }

    val allMethods: Collection<RustImplMethodMemberElement>
        get() = inherentImpls.flatMap { it.implBody?.implMethodMemberList.orEmpty() }

    val nonStaticMethods: Collection<RustImplMethodMemberElement>
        get() = allMethods.filter { !it.isStatic }

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitStruct(this)

    override fun equals(other: Any?): Boolean = other is RustStructType && other.struct === struct

    override fun hashCode(): Int = struct.hashCode() * 10067 + 9631

    override fun toString(): String = struct.name ?: "<anonymous>"
}
