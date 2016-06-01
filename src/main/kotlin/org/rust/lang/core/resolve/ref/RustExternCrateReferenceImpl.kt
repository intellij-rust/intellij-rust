package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.RustExternCrateItem
import org.rust.lang.core.resolve.RustResolveEngine

class RustExternCrateReferenceImpl(externCrate: RustExternCrateItem)
    : RustReferenceBase<RustExternCrateItem>(externCrate, externCrate.identifier)
    , RustReference {

    override fun getVariants(): Array<out Any> = emptyArray()

    override fun resolveImpl(): RustResolveEngine.ResolveResult = RustResolveEngine.resolveExternCrate(element)
}
