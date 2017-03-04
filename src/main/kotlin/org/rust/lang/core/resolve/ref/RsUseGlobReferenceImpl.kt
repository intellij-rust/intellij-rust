package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.CompletionEngine
import org.rust.lang.core.psi.RsUseGlob
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.ResolveEngine

class RsUseGlobReferenceImpl(
    useGlob: RsUseGlob
) : RsReferenceBase<RsUseGlob>(useGlob),
    RsReference {

    override val RsUseGlob.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        CompletionEngine.completeUseGlob(element)

    override fun resolveInner(): List<RsCompositeElement> = ResolveEngine.resolveUseGlob(element)
}


