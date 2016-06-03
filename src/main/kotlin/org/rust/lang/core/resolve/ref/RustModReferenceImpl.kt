package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.RustModDeclItemElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustModReferenceImpl(modDecl: RustModDeclItemElement)
    : RustReferenceBase<RustModDeclItemElement>(modDecl, modDecl.identifier)
    , RustReference {

    override fun resolveVerbose(): RustResolveEngine.ResolveResult = RustResolveEngine.resolveModDecl(element)

    override fun getVariants(): Array<out Any> = EMPTY_ARRAY
}
