package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLifetimeReference
import org.rust.lang.core.psi.RsNamedElement

class RsLifetimeReferenceReferenceImpl(
    lifetimeRef: RsLifetimeReference
) : RsReferenceBase<RsLifetimeReference>(lifetimeRef),
    RsReference {

    override val RsLifetimeReference.referenceAnchor: PsiElement get() = lifetime

    override fun resolveInner(): List<RsNamedElement> = emptyList()

    override fun getVariants(): Array<out Any> = emptyArray()
}
