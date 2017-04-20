package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.*


class RsPathReferenceImpl(
    element: RsPath
) : RsReferenceBase<RsPath>(element),
    RsReference {

    override val RsPath.referenceAnchor: PsiElement get() = referenceNameElement

    override fun resolveInner(): List<RsCompositeElement> =
        collectResolveVariants(element.referenceName) { processPathResolveVariants(element, false, it) }

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processPathResolveVariants(element, true, it) }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val target = resolve()
        return element.manager.areElementsEquivalent(target, element)
    }
}
