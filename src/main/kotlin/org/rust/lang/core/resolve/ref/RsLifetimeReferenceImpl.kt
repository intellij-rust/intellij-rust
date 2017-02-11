package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetimeReference
import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.resolve.ResolveEngine

class RsLifetimeReferenceReferenceImpl(
    element: RsLifetimeReference
) : RsReferenceBase<RsLifetimeReference>(element),
    RsReference {

    override val RsLifetimeReference.referenceAnchor: PsiElement get() = lifetime

    override fun resolveInner(): List<RsNamedElement> = listOfNotNull(ResolveEngine.resolveLifetime(element))

    override fun getVariants(): Array<out Any> = emptyArray()
}
