package org.rust.lang.core.psi

public interface RustDeclaringElement : RustCompositeElement {

    val boundElements: Collection<RustNamedElement>

}

