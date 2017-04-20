package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.*

class RsExternCrateReferenceImpl(
    externCrate: RsExternCrateItem
) : RsReferenceBase<RsExternCrateItem>(externCrate),
    RsReference {

    override val RsExternCrateItem.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processExternCrateResolveVariants(element, true, it) }

    override fun resolveInner(): List<RsCompositeElement> =
        collectResolveVariants(element.name!!) { processExternCrateResolveVariants(element, false, it) }
}
