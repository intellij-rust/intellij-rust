package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.psi.RustExternCrateItem
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.util.parentRelativeRange
import org.rust.lang.core.resolve.RustResolveEngine

class RustExternCrateReferenceImpl(externCrate: RustExternCrateItem)
    : PsiReferenceBase<RustExternCrateItem>(externCrate, externCrate.identifier.parentRelativeRange)
    , RustReference {

    override fun getVariants(): Array<out Any> = emptyArray()

    override fun resolve(): RustNamedElement? = RustResolveEngine.resolveExternCrate(element).element
}
