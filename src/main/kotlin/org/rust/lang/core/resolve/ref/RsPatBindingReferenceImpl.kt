package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.*


class RsPatBindingReferenceImpl(
    element: RsPatBinding
) : RsReferenceBase<RsPatBinding>(element),
    RsReference {

    override val RsPatBinding.referenceAnchor: PsiElement get() = referenceNameElement

    override fun resolveInner(): List<RsCompositeElement> =
        collectResolveVariants(element.referenceName) { processPatBindingResolveVariants(element, false, it) }

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processPatBindingResolveVariants(element, true, it) }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val target = resolve()
        return element.manager.areElementsEquivalent(target, element)
    }
}
