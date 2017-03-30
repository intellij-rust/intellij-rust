package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsUseGlob
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.CompletionProcessor
import org.rust.lang.core.resolve.MultiResolveProcessor
import org.rust.lang.core.resolve.processResolveVariants

class RsUseGlobReferenceImpl(
    useGlob: RsUseGlob
) : RsReferenceBase<RsUseGlob>(useGlob),
    RsReference {

    override val RsUseGlob.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        CompletionProcessor().run { processResolveVariants(element, it) }

    override fun resolveInner(): List<RsCompositeElement> =
        MultiResolveProcessor(element.referenceName).run { processResolveVariants(element, it) }
}


