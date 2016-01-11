package org.rust.lang.core.psi

interface RustAccessControlElement: RustCompositeElement {
    val isPublic: Boolean
}
