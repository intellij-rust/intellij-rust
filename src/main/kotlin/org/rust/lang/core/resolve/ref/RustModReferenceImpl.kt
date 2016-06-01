package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.resolve.RustResolveEngine

class RustModReferenceImpl(modDecl: RustModDeclItem)
    : RustReferenceBase<RustModDeclItem>(modDecl, modDecl.identifier)
    , RustReference {

    override fun resolveImpl(): RustResolveEngine.ResolveResult = RustResolveEngine.resolveModDecl(element)

    override fun getVariants(): Array<out Any> = EMPTY_ARRAY
}
