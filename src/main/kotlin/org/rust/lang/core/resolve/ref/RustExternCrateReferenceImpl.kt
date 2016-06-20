package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustExternCrateItemElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustExternCrateReferenceImpl(externCrate: RustExternCrateItemElement)
    : RustReferenceBase<RustExternCrateItemElement>(externCrate)
    , RustReference {

    override val RustExternCrateItemElement.referenceAnchor: PsiElement get() = identifier

    override fun getVariants(): Array<out Any> = emptyArray()

    override fun resolveVerbose(): RustResolveEngine.ResolveResult = RustResolveEngine.resolveExternCrate(element)
}
