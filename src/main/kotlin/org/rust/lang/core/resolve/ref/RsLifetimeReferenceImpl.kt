package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processResolveVariants

class RsLifetimeReferenceImpl(
    element: RsLifetime
) : RsReferenceBase<RsLifetime>(element),
    RsReference {

    override val RsLifetime.referenceAnchor: PsiElement get() = quoteIdentifier

    override fun resolveInner(): List<RsCompositeElement> =
        collectResolveVariants(element.referenceName) { processResolveVariants(element, it) }

    override fun getVariants(): Array<out Any> = emptyArray()
}
