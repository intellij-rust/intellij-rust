package org.rust.lang.core.psi

public interface RustDeclaringElement : RustCompositeElement {

    fun getBoundElements(): Collection<RustNamedElement>

}

