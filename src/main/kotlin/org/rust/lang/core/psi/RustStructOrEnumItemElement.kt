package org.rust.lang.core.psi

interface RustStructOrEnumItemElement : RustNamedElement, RustTypeBearingItemElement, RustGenericDeclaration {

    /**
     * Impls without traits, like `impl S { ... }`
     *
     * You don't need to import such impl to be able to use its methods.
     * There may be several `impl` blocks for the same type and they may
     * be spread across different files and modules (we don't handle this yet)
     */
    val inherentImpls: Sequence<RustImplItemElement> get() = emptySequence()

}
