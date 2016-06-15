package org.rust.lang.core.psi

import org.rust.lang.core.resolve.scope.RustResolveScope

interface RustFnElement: RustNamedElement, RustGenericDeclaration, RustResolveScope, RustInnerAttributeOwner, RustOuterAttributeOwner {
    val parameters: RustParametersElement?
    val block: RustBlockElement?
    val retType: RustRetTypeElement?
}

val RustFnElement.isStatic: Boolean get() = parameters?.selfArgument == null
