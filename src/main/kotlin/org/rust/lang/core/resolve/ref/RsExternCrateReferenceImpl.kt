package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.CompletionEngine
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.resolve.ResolveEngine

class RsExternCrateReferenceImpl(
    externCrate: RsExternCrateItem
) : RsReferenceBase<RsExternCrateItem>(externCrate),
    RsReference {

    override val RsExternCrateItem.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> = CompletionEngine.completeExternCrate(element)

    override fun resolveInner(): List<RsNamedElement> = listOfNotNull(ResolveEngine.resolveExternCrate(element))
}
