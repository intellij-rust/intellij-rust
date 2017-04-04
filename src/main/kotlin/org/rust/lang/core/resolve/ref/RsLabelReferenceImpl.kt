package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLabel
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processResolveVariants

class RsLabelReferenceImpl(
    element: RsLabel
) : RsReferenceBase<RsLabel>(element),
    RsReference {

    override val RsLabel.referenceAnchor: PsiElement get() = quoteIdentifier

    override fun resolveInner(): List<RsCompositeElement> =
        collectResolveVariants(element.referenceName) { processResolveVariants(element, it) }

    override fun getVariants(): Array<out Any> = emptyArray()
}
