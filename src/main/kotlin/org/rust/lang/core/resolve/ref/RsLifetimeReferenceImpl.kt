package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.resolve.ResolveEngine

class RsLifetimeReferenceImpl(
    element: RsLifetime
) : RsReferenceBase<RsLifetime>(element),
    RsReference {

    override val RsLifetime.referenceAnchor: PsiElement get() = quoteIdentifier

    override fun resolveInner(): List<RsNamedElement> = listOfNotNull(ResolveEngine.resolveLifetime(element))

    override fun getVariants(): Array<out Any> = emptyArray()
}
