package org.rust.lang.core.psi

interface RustDeclaringElement : RustCompositeElement {

    val boundElements: Collection<RustNamedElement>

}

