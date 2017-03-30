package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.CompletionProcessor
import org.rust.lang.core.resolve.MultiResolveProcessor
import org.rust.lang.core.resolve.processResolveVariants

class RsExternCrateReferenceImpl(
    externCrate: RsExternCrateItem
) : RsReferenceBase<RsExternCrateItem>(externCrate),
    RsReference {

    override val RsExternCrateItem.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        CompletionProcessor().run { processResolveVariants(element, true, it) }

    override fun resolveInner(): List<RsCompositeElement> =
        MultiResolveProcessor(element.name!!).run { processResolveVariants(element, false, it) }
}
