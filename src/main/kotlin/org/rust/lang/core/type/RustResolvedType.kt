package org.rust.lang.core.type

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.impl.mixin.isStatic


interface RustResolvedType {
    /**
     * Impls without traits, like `impl S { ... }`
     *
     * You don't need to import such impl to be able to use its methods.
     * There may be several `impl` blocks for the same type and they may
     * be spread across different files and modules (we don't handle this yet)
     */
    val inheritedImpls: Collection<RustImplItemElement>

    val allMethods: Collection<RustImplMethodMemberElement>
        get() = inheritedImpls.flatMap { it.implBody?.implMethodMemberList.orEmpty() }

    val nonStaticMethods: Collection<RustImplMethodMemberElement>
        get() = allMethods.filter { !it.isStatic }

}
