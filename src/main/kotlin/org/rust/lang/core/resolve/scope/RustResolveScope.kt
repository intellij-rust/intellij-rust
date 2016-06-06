package org.rust.lang.core.resolve.scope

import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement

interface RustResolveScope : RustCompositeElement {
    val declarations: Collection<RustNamedElement>
}

//val RustResolveScope.boundElements: Collection<RustNamedElement>
//    get() = declarations.flatMap { it.boundElements }

