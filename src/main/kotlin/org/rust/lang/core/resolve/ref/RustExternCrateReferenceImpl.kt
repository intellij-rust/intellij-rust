package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.RustExternCrateItemElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustExternCrateReferenceImpl(externCrate: RustExternCrateItemElement)
    : RustReferenceBase<RustExternCrateItemElement>(externCrate, externCrate.identifier)
    , RustReference {

    override fun getVariants(): Array<out Any> = emptyArray()

    override fun resolveVerbose(): RustResolveEngine.ResolveResult = RustResolveEngine.resolveExternCrate(element)
}
