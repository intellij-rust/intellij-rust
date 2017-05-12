package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsUseGlob
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.BoundElement

class RsUseGlobReferenceImpl(
    useGlob: RsUseGlob
) : RsReferenceBase<RsUseGlob>(useGlob),
    RsReference {

    override val RsUseGlob.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processUseGlobResolveVariants(element, it) }

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> =
        collectResolveVariants(element.referenceName) { processUseGlobResolveVariants(element, it) }
}


