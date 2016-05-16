package org.rust.lang.core.type

import org.rust.lang.core.psi.RustImplItem
import org.rust.lang.core.psi.RustImplMethodMember
import org.rust.lang.core.psi.impl.mixin.isStatic


interface RustResolvedType {
    /**
     * Impls without traits, like `impl S { ... }`
     *
     * You don't need to import such impl to be able to use its methods.
     * There may be several `impl` blocks for the same type and they may
     * be spread across different files and modules (we don't handle this yet)
     */
    val inheritedImpls: Collection<RustImplItem>

    val allMethods: Collection<RustImplMethodMember>
        get() = inheritedImpls.flatMap { it.implBody?.implMethodMemberList.orEmpty() }

    val nonStaticMethods: Collection<RustImplMethodMember>
        get() = allMethods.filter { !it.isStatic }

}
